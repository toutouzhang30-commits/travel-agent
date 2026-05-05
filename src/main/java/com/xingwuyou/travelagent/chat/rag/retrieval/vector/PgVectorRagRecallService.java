package com.xingwuyou.travelagent.chat.rag.retrieval.vector;


import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionManifestRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionProperties;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagRetrievalResult;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

//嵌入-过滤-检索
@Service
public class PgVectorRagRecallService {
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final RagIngestionProperties ingestionProperties;
    private final RagIngestionManifestRepository manifestRepository;
    private final String vectorTable;

    public PgVectorRagRecallService(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            RagIngestionProperties ingestionProperties,
            RagIngestionManifestRepository manifestRepository,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:travel_knowledge}") String tableName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.ingestionProperties = ingestionProperties;
        this.manifestRepository = manifestRepository;
        this.vectorTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
    }
    public List<RagRetrievalResult> retrieve(RagQuery query) {
        String activeRunId = manifestRepository.findActiveRunId(ingestionProperties.getNamespace())
                .orElse(null);

        if (activeRunId == null || activeRunId.isBlank()) {
            return List.of();
        }

        float[] embedding = embeddingModel.embed(query.userQuestion());
        String vectorLiteral = toVectorLiteral(embedding);

        return retrieveOnce(query, activeRunId, vectorLiteral);
    }

    private List<RagRetrievalResult> retrieveOnce(
            RagQuery query,
            String activeRunId,
            String vectorLiteral
    ) {
        String city = blankToNull(query.city());
        String topic = blankToNull(query.topic());

        String sql = """
        select
            content,
            metadata ->> 'city' as city,
            metadata ->> 'topic' as topic,
            metadata ->> 'sourceName' as source_name,
            metadata ->> 'sourceUrl' as source_url,
            metadata ->> 'verifiedAt' as verified_at,
            embedding <=> ?::vector as distance
        from %s
        where metadata ->> 'ingestionNamespace' = ?
          and metadata ->> 'ingestionRunId' = ?
          -- 关键修改点：给判断 null 的参数加上 ::text 强转
          and (?::text is null or metadata ->> 'city' = ?)
          and (?::text is null or metadata ->> 'topic' = ?)
        order by distance asc
        limit ?
        """.formatted(vectorTable);


        List<RagRetrievalResult> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    double distance = rs.getDouble("distance");
                    double similarity = cosineDistanceToSimilarity(distance);

                    return new RagRetrievalResult(
                            rs.getString("content"),
                            rs.getString("city"),
                            rs.getString("topic"),
                            rs.getString("source_name"),
                            rs.getString("source_url"),
                            rs.getString("verified_at"),
                            similarity,
                            distance
                    );
                },
                vectorLiteral,
                ingestionProperties.getNamespace(),
                activeRunId,
                city,
                city,
                topic,
                topic,
                query.topK()
        );

        if (!results.isEmpty()) {
            return results;
        }

        //递归回退策略
        if (topic != null) {
            return retrieveOnce(
                    new RagQuery(query.userQuestion(), query.city(), null, query.topK()),
                    activeRunId,
                    vectorLiteral
            );
        }

        if (city != null) {
            return retrieveOnce(
                    new RagQuery(query.userQuestion(), null, null, query.topK()),
                    activeRunId,
                    vectorLiteral
            );
        }

        return results;
    }

    //相似度的计算
    private double cosineDistanceToSimilarity(double distance) {
        double similarity = 1.0 - distance;
        if (similarity < 0.0) {
            return 0.0;
        }
        if (similarity > 1.0) {
            return 1.0;
        }
        return similarity;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(embedding[i]);
        }
        return builder.append("]").toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}
