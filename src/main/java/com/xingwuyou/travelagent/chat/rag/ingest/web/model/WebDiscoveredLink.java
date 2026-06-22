package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

public record WebDiscoveredLink( String url,
                                 String linkText,
                                 String imageAlt,
                                 String linkType,
                                 int discoveredDepth) {
}
