package com.xingwuyou.travelagent.chat.rag.ingest.web.model;

import java.util.List;

public record WebPageExtraction(
        String title,
        String mainText,
        WebPageType pageType,
        List<WebDiscoveredLink> links,
        double contentScore,
        double linkDensity,
        double textDensity,
        String extractionMethod,
        boolean rendered
) {
    public String preview(int maxChars) {
        if (mainText == null) return "";
        return mainText.length() <= maxChars ? mainText : mainText.substring(0, maxChars);
    }
}