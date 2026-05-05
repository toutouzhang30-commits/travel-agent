package com.xingwuyou.travelagent.chat.rag.retrieval.lexical;

import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionManifestRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionProperties;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagRecallSource;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static org.apache.lucene.document.Field.Store.YES;

@Service
public class LexicalRagRecallService {

    private final JdbcTemplate jdbcTemplate;
    private final RagIngestionProperties ingestionProperties;
    private final RagIngestionManifestRepository manifestRepository;
    private final String vectorTable;
    private final int topK;

    private IndexState indexState;

    public LexicalRagRecallService(
            JdbcTemplate jdbcTemplate,
            RagIngestionProperties ingestionProperties,
            RagIngestionManifestRepository manifestRepository,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:travel_knowledge}") String tableName,
            @Value("${travel.rag.lexical.top-k:10}") int topK
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ingestionProperties = ingestionProperties;
        this.manifestRepository = manifestRepository;
        this.vectorTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        this.topK = topK;
    }

    public synchronized void rebuildIndex() {
        String namespace = ingestionProperties.getNamespace();
        String activeRunId = manifestRepository.findActiveRunId(namespace).orElse(null);

        if (activeRunId == null || activeRunId.isBlank()) {
            indexState = IndexState.empty();
            return;
        }

        List<RagIndexDocument> rows = loadActiveRunDocuments(namespace, activeRunId);
        if (rows.isEmpty()) {
            indexState = IndexState.empty();
            return;
        }

        try {
            Analyzer analyzer = new SmartChineseAnalyzer();
            Directory directory = new ByteBuffersDirectory();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (RagIndexDocument row : rows) {
                    Document document = new Document();
                    document.add(new TextField("content", row.content(), YES));
                    document.add(new StringField("city", nullToEmpty(row.city()), YES));
                    document.add(new StringField("topic", nullToEmpty(row.topic()), YES));
                    document.add(new StoredField("sourceName", nullToEmpty(row.sourceName())));
                    document.add(new StoredField("sourceUrl", nullToEmpty(row.sourceUrl())));
                    document.add(new StoredField("verifiedAt", nullToEmpty(row.verifiedAt())));
                    writer.addDocument(document);
                }
            }

            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            indexState = new IndexState(activeRunId, rows.size(), analyzer, directory, reader, searcher);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to rebuild Lucene BM25 RAG index", ex);
        }
    }

    public List<RagScoredDocument> search(RagQuery query) {
        ensureIndexFresh();

        if (indexState == null || indexState.isEmpty() || query == null || query.userQuestion().isBlank()) {
            return List.of();
        }


        try {
            QueryParser parser = new QueryParser("content", indexState.analyzer());
            Query contentQuery = parser.parse(QueryParser.escape(query.userQuestion()));

            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(contentQuery, BooleanClause.Occur.MUST);

            if (query.city() != null && !query.city().isBlank()) {
                builder.add(new TermQuery(new Term("city", query.city())), BooleanClause.Occur.FILTER);
            }

            TopDocs topDocs = indexState.searcher().search(builder.build(), Math.max(query.topK(), topK));

            return java.util.Arrays.stream(topDocs.scoreDocs)
                    .map(hit -> toScoredDocument(hit))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void ensureIndexFresh() {
        String activeRunId = manifestRepository.findActiveRunId(ingestionProperties.getNamespace()).orElse(null);
        int activeCount = manifestRepository.countRunDocuments(ingestionProperties.getNamespace(), activeRunId);

        if (indexState == null
                || !indexState.activeRunId().equals(activeRunId)
                || indexState.chunkCount() != activeCount) {
            rebuildIndex();
        }
    }

    private RagScoredDocument toScoredDocument(ScoreDoc hit) {
        try {
            Document document = indexState.searcher().storedFields().document(hit.doc);
            double lexicalScore = normalizeBm25(hit.score);

            return new RagScoredDocument(
                    document.get("content"),
                    document.get("city"),
                    document.get("topic"),
                    document.get("sourceName"),
                    document.get("sourceUrl"),
                    document.get("verifiedAt"),
                    lexicalScore,
                    0.0,
                    lexicalScore,
                    0.0,
                    0,
                    "BM25",
                    0.0,
                    true,
                    Set.of(RagRecallSource.LEXICAL)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map Lucene BM25 hit", ex);
        }
    }

    private List<RagIndexDocument> loadActiveRunDocuments(String namespace, String activeRunId) {
        return jdbcTemplate.query("""
                select
                    content,
                    metadata ->> 'city' as city,
                    metadata ->> 'topic' as topic,
                    metadata ->> 'sourceName' as source_name,
                    metadata ->> 'sourceUrl' as source_url,
                    metadata ->> 'verifiedAt' as verified_at
                from %s
                where metadata ->> 'ingestionNamespace' = ?
                  and metadata ->> 'ingestionRunId' = ?
                """.formatted(vectorTable),
                (rs, rowNum) -> new RagIndexDocument(
                        rs.getString("content"),
                        rs.getString("city"),
                        rs.getString("topic"),
                        rs.getString("source_name"),
                        rs.getString("source_url"),
                        rs.getString("verified_at")
                ),
                namespace,
                activeRunId
        );
    }

    private double normalizeBm25(float score) {
        return score <= 0 ? 0.0 : score / (score + 10.0);
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RagIndexDocument(
            String content,
            String city,
            String topic,
            String sourceName,
            String sourceUrl,
            String verifiedAt
    ) {}

        private record IndexState(
                String activeRunId,
                int chunkCount,
                Analyzer analyzer,
                Directory directory,
                DirectoryReader reader,
                IndexSearcher searcher
        ) {
            static IndexState empty() {
                return new IndexState("", 0, null, null, null, null);
            }

            boolean isEmpty() {
                return searcher == null;
            }
        }
}
