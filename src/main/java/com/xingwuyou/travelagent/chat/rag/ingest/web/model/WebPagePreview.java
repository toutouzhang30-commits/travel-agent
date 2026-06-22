package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebPagePreview(
        String url,
        String title,
        String snippet,
        WebPageType pageTypeHint,
        double contentScore,
        double linkDensity,
        boolean rendered
) {}