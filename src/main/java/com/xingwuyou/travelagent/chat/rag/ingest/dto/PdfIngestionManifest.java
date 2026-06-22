package com.xingwuyou.travelagent.chat.rag.ingest.dto;

import java.util.List;

public record PdfIngestionManifest(
        boolean enabled,
        List<PdfSource> sources
) {
    public List<PdfSource> safeSources() {
        return sources == null ? List.of() : sources;
    }

    public record PdfSource(
            String id,
            String city,
            String category,
            String sourceName,
            String path,
            String verifiedAt,
            String confidenceLevel
    ) {
    }
}