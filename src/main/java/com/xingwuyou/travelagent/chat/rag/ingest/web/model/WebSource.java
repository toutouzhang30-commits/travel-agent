package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebSource(
        Long id,
                          String city,
                          String topic,
                          String sourceName,
                          String sourceUrl,
                          String confidenceLevel,
                          String lastContentHash,
                          Integer crawlDepth,
                          String crawlPurpose,
                          Long parentSourceId,
                          String canonicalUrl
) {
    public int depth() {
        return crawlDepth == null ? 0 : crawlDepth;
    }
}