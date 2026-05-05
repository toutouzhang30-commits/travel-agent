package com.xingwuyou.travelagent.chat.rag.retrieval.model;

import java.util.Set;

public record RagScoredDocument(
        String content,
        String city,
        String topic,
        String sourceName,
        String sourceUrl,
        String verifiedAt,
        double score,
        double vectorScore,
        double lexicalScore,
        double rerankScore,
        int rerankRank,
        String scoreSource,
        double keywordHitRate,
        boolean cityMatched,
        Set<RagRecallSource> recallSources
) {
}
