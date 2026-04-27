package com.xingwuyou.travelagent.chat.dto;

public record ToolCallPayloadDto( String toolName,
                                  String summary,
                                  String requestedAt) {
}
