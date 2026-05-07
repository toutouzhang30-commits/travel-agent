package com.xingwuyou.travelagent.chat.tool.common;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;

public record TravelToolResult(TravelToolName toolName,
                               boolean success,
                               String summary,
                               SourceReferenceDto source,
                               String updatedAt,
                               String errorMessage,
                               Object payload) {
}
