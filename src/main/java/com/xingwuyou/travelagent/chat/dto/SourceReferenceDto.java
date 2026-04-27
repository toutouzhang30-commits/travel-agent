package com.xingwuyou.travelagent.chat.dto;

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
        String toolName
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
                null
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
                toolName
        );
    }
}
