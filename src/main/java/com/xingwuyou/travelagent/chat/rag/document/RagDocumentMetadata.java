package com.xingwuyou.travelagent.chat.rag.document;

// 最小文档元数据
public record RagDocumentMetadata(
        String city,
        String category,
        String topic,
        String sourceName,
        String sourceUrl,
        String verifiedAt,
        String confidenceLevel,
        String sourceType,
        String sourceId,
        String sourceVersionId,
        String fetchedAt,
        String documentContentHash,
        String sourceTopic,
        String pageType,
        String sectionTitle,
        String sectionSummary,
        String pageStart,
        String pageEnd
) {
}