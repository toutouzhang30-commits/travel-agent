package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebSourceVersion(
        Long id,
        Long sourceId,
        String title,
        String cleanedContent,
        String contentHash,
        String fetchedAt,
        String primaryTopic,
        String pageType
) {}