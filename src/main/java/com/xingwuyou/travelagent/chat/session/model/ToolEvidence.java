package com.xingwuyou.travelagent.chat.session.model;

public record ToolEvidence(String toolName,
                           boolean success,
                           String city,
                           String summary,
                           String sourceName,
                           String updatedAt,
                           String errorMessage) {
}
