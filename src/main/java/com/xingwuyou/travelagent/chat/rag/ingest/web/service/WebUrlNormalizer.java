package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

//新增url工具
@Service
public class WebUrlNormalizer {
    private static final Set<String> RESOURCE_SUFFIXES = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
            ".pdf", ".zip", ".mp4", ".mp3", ".css", ".js"
    );

    public String canonicalize(String url) {
        try {
            URI uri = URI.create(url).normalize();
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            String query = uri.getQuery();

            URI cleaned = new URI(scheme, null, host, uri.getPort(), path, query, null);
            return cleaned.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean sameHost(String parentUrl, String childUrl) {
        try {
            URI parent = URI.create(parentUrl);
            URI child = URI.create(childUrl);
            return parent.getHost() != null
                    && child.getHost() != null
                    && parent.getHost().equalsIgnoreCase(child.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHtmlCandidate(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return RESOURCE_SUFFIXES.stream().noneMatch(lower::endsWith);
    }
}