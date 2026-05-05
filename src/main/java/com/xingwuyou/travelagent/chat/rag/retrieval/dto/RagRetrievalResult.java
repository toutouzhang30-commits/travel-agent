package com.xingwuyou.travelagent.chat.rag.retrieval.dto;

//检索结果
public record RagRetrievalResult(
        String content,
        String city,
        String topic,
        String sourceName,
        String sourceUrl,
        String verifiedAt,
        double score,
        //Pgvector的原始距离
        double distance
) {
}
