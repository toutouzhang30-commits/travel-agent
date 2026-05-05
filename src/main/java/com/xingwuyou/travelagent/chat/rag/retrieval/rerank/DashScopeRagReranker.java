package com.xingwuyou.travelagent.chat.rag.retrieval.rerank;


import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import com.alibaba.dashscope.utils.JsonUtils;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

//重排器，截断，排序，兜底
@Component
@ConditionalOnProperty(
        prefix = "travel.rag.rerank",
        name = "enabled",
        havingValue = "true"
)
public class DashScopeRagReranker implements RagReranker {
    private final ObjectMapper objectMapper;
    private final String model;
    private final int topN;
    private final String apiKey;

    public DashScopeRagReranker(
            ObjectMapper objectMapper,
            @Value("${travel.rag.rerank.api-key:}") String apiKey,
            @Value("${travel.rag.rerank.model:gte-rerank-v2}") String model,
            @Value("${travel.rag.rerank.top-n:10}") int topN
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.topN = topN;
    }

    @Override
    public RagRerankResult rerank(RagQuery query, List<RagScoredDocument> candidates) {
        long startedAt = System.nanoTime();
        if (apiKey == null || apiKey.isBlank()) {
            return fallback(candidates, startedAt, "rerank skipped: missing DASHSCOPE_API_KEY");
        }


        if (query == null || query.userQuestion() == null || query.userQuestion().isBlank()) {
            return fallback(candidates, startedAt, "rerank skipped: empty query");
        }

        if (candidates == null || candidates.isEmpty()) {
            return fallback(List.of(), startedAt, "rerank skipped: empty candidates");
        }

        try {
            List<String> documents = candidates.stream()
                    .map(RagScoredDocument::content)
                    .map(this::truncateForRerank)
                    .toList();

            TextReRankParam param = TextReRankParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .query(query.userQuestion())
                    .documents(documents)
                    .topN(Math.min(topN, documents.size()))
                    .returnDocuments(false)
                    .build();


            TextReRank textReRank = new TextReRank();
            TextReRankResult result = textReRank.call(param);

            List<RagScoredDocument> rerankedDocuments = parseAndApplyRerankResult(
                    JsonUtils.toJson(result),
                    candidates
            );

            if (rerankedDocuments.isEmpty()) {
                return fallback(candidates, startedAt, "rerank returned empty results");
            }

            long rerankTimeMs = elapsedMs(startedAt);
            return RagRerankResult.reranked(rerankedDocuments, rerankTimeMs);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            return fallback(
                    candidates,
                    startedAt,
                    "rerank failed: " + ex.getClass().getName() + " " + message
            );
        }
    }

    //结果解析和索引回填
    private List<RagScoredDocument> parseAndApplyRerankResult(
            String json,
            List<RagScoredDocument> candidates
    ) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("output").path("results");

        if (!results.isArray()) {
            return List.of();
        }

        List<RerankHit> hits = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            double score = item.path("relevance_score").asDouble(Double.NaN);

            if (index < 0 || index >= candidates.size() || Double.isNaN(score)) {
                continue;
            }

            hits.add(new RerankHit(index, score));
        }

        hits.sort(Comparator.comparingDouble(RerankHit::score).reversed());

        List<RagScoredDocument> reranked = new ArrayList<>();
        Set<Integer> usedIndexes = new HashSet<>();

        int rank = 1;
        for (RerankHit hit : hits) {
            RagScoredDocument original = candidates.get(hit.index());
            reranked.add(withRerankScore(original, hit.score(), rank));
            usedIndexes.add(hit.index());
            rank++;
        }

        for (int i = 0; i < candidates.size(); i++) {
            if (!usedIndexes.contains(i)) {
                RagScoredDocument original = candidates.get(i);
                reranked.add(withFallbackScore(original, rank));
                rank++;
            }
        }

        return reranked;
    }

    //文档重构
    private RagScoredDocument withRerankScore(
            RagScoredDocument original,
            double rerankScore,
            int rerankRank
    ) {
        return new RagScoredDocument(
                original.content(),
                original.city(),
                original.topic(),
                original.sourceName(),
                original.sourceUrl(),
                original.verifiedAt(),
                rerankScore,
                original.vectorScore(),
                original.lexicalScore(),
                rerankScore,
                rerankRank,
                "DASHSCOPE_RERANK",
                original.keywordHitRate(),
                original.cityMatched(),
                original.recallSources()
        );
    }

    private RagScoredDocument withFallbackScore(
            RagScoredDocument original,
            int rerankRank
    ) {
        return new RagScoredDocument(
                original.content(),
                original.city(),
                original.topic(),
                original.sourceName(),
                original.sourceUrl(),
                original.verifiedAt(),
                original.score(),
                original.vectorScore(),
                original.lexicalScore(),
                original.rerankScore(),
                rerankRank,
                original.scoreSource(),
                original.keywordHitRate(),
                original.cityMatched(),
                original.recallSources()
        );
    }

    private RagRerankResult fallback(
            List<RagScoredDocument> candidates,
            long startedAt,
            String reason
    ) {
        return RagRerankResult.fallback(candidates, elapsedMs(startedAt), reason);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    //内容截断
    private String truncateForRerank(String content) {
        if (content == null) {
            return "";
        }

        int maxChars = 1800;
        if (content.length() <= maxChars) {
            return content;
        }

        return content.substring(0, maxChars);
    }

    private record RerankHit(int index, double score) {
    }
}
