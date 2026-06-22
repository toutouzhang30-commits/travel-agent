package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebFetchedPage( String requestedUrl,
                              String finalUrl,
                              String title,
                              String html,
                              org.jsoup.nodes.Document document,
                              boolean rendered,
                              java.time.OffsetDateTime fetchedAt) {
}
