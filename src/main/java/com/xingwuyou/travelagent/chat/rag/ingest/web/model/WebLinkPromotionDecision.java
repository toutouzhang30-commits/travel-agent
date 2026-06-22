package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebLinkPromotionDecision(
        WebPageType pageKind,
        boolean containsCoreInfo,
        String preliminaryTopic,
        String city,
        double confidence,
        String rejectReason
) {
}