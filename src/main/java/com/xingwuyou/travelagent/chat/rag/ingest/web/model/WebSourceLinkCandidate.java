package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebSourceLinkCandidate(
        Long id,
        Long parentSourceId,
        String discoveredUrl,
        String linkText,
        String imageAlt,
        String linkType,
        int discoveredDepth
) {
}