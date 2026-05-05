package com.xingwuyou.travelagent.chat.rag.retrieval.service;

import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionManifestRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionProperties;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagRetrievalResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

//根据问题去做相似度搜索
@Service
public class RagRetrievalService {
    private final VectorStore vectorStore;
    private final RagIngestionProperties ingestionProperties;
    private final RagIngestionManifestRepository manifestRepository;

    public RagRetrievalService(VectorStore vectorStore,
                               RagIngestionProperties ingestionProperties,
                               RagIngestionManifestRepository manifestRepository) {
        this.vectorStore = vectorStore;
        this.ingestionProperties = ingestionProperties;
        this.manifestRepository = manifestRepository;
    }

    //严格检索失败后降级检索
    public List<RagRetrievalResult> retrieve(RagQuery query) {
        //全条件精准检索
        List<RagRetrievalResult> results = retrieveOnce(query);

        if (!results.isEmpty()) {
            return results;
        }

        if (query.topic() != null && !query.topic().isBlank()) {
            results = retrieveOnce(new RagQuery(
                    query.userQuestion(),
                    query.city(),
                    null,
                    query.topK()
            ));
        }

        if (!results.isEmpty()) {
            return results;
        }

        if (query.city() != null && !query.city().isBlank()) {
            return retrieveOnce(new RagQuery(
                    query.userQuestion(),
                    null,
                    null,
                    query.topK()
            ));
        }

        return results;
    }

    //与向量数据库的交互
    private List<RagRetrievalResult> retrieveOnce(RagQuery query) {
        SearchRequest request = SearchRequest.builder()
                .query(query.userQuestion())
                .topK(query.topK())
                .filterExpression(buildFilter(query))
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);

        return documents.stream()
                .map(this::toResult)
                .toList();
    }



    //精准过滤
    private  String buildFilter(RagQuery query) {
        List<String> filters = new java.util.ArrayList<>();

        manifestRepository.findActiveRunId(ingestionProperties.getNamespace())
                .ifPresent(runId -> {
                    filters.add("ingestionNamespace == '" + escape(ingestionProperties.getNamespace()) + "'");
                    filters.add("ingestionRunId == '" + escape(runId) + "'");
                });

        if (query.city() != null && !query.city().isBlank()) {
            filters.add("city == '" + escape(query.city()) + "'");
        }

        if (query.topic() != null && !query.topic().isBlank()) {
            filters.add("topic == '" + escape(query.topic()) + "'");
        }

        return String.join(" && ", filters);
    }
    private String escape(String value) {
        return value.replace("'", "\\'");
    }

    //这是干什么？value函数在干什么？
    //官方对象翻译成给前端的我的对象
    private RagRetrievalResult toResult(Document document) {
        return new RagRetrievalResult(
                document.getText(),
                value(document, "city"),
                value(document, "topic"),
                value(document, "sourceName"),
                value(document, "sourceUrl"),
                value(document, "verifiedAt"),
                score(document),
                distance(document)
        );
    }

    //尝试从 metadata 字典中翻找名为 score 或 similarity 的键值
    private double score(Document document) {
        Object value = firstMetadataValue(document, "score", "similarity");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    //从 metadata 里寻找 distance（向量距离）
    private double distance(Document document) {
        Object value = firstMetadataValue(document, "distance");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private Object firstMetadataValue(Document document, String... keys) {
        for (String key : keys) {
            Object value = document.getMetadata().get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }


    private String value(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }
}
