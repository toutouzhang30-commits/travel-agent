package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

import java.util.List;

public record WebKnowledgeMetadata(
        WebPageType pageType,
        String primaryTopic,
        List<String> secondaryTopics,
        boolean containsCoreInfo,
        boolean suitableForRag,
        String rejectReason
) {}