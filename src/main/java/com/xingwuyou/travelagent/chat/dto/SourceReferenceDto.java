package com.xingwuyou.travelagent.chat.dto;

import java.util.List;

//返回给前端的来源信息
public record SourceReferenceDto(
        String sourceType,
        String city,
        String topic,
        String sourceName,
        String sourceUrl,
        String verifiedAt,
        String effectiveAt,
        String summary,
        String toolName,
        List<String> recallSources,
        String scoreSource,
        Double score,
        Double vectorScore,
        Double lexicalScore,
        Double rerankScore
) {

    //数据库
    public static SourceReferenceDto rag(
            String city,
            String topic,
            String sourceName,
            String sourceUrl,
            String verifiedAt
    ) {
        return new SourceReferenceDto(
                "RAG",
                city,
                topic,
                sourceName,
                sourceUrl,
                verifiedAt,
                verifiedAt,
                "知识库检索",
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public static SourceReferenceDto rag(
            String city,
            String topic,
            String sourceName,
            String sourceUrl,
            String verifiedAt,
            List<String> recallSources,
            String scoreSource,
            double score,
            double vectorScore,
            double lexicalScore,
            double rerankScore
    ) {
        return new SourceReferenceDto(
                "RAG",
                city,
                topic,
                sourceName,
                sourceUrl,
                verifiedAt,
                verifiedAt,
                "知识库检索",
                null,
                recallSources == null ? List.of() : recallSources,
                scoreSource,
                score,
                vectorScore,
                lexicalScore,
                rerankScore
        );
    }

    //工具
    public static SourceReferenceDto tool(
            String city,
            String sourceName,
            String sourceUrl,
            String effectiveAt,
            String summary,
            String toolName
    ) {
        return new SourceReferenceDto(
                "TOOL",
                city,
                "实时工具",
                sourceName,
                sourceUrl,
                effectiveAt,
                effectiveAt,
                summary,
                toolName,
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
