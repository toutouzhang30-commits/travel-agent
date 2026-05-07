package com.xingwuyou.travelagent.chat.tool.common;

public record ToolExecutionContext(String sessionId,
                                   String rawQuestion,
                                   String fallbackCity,
                                   String requestedAt) {
}
