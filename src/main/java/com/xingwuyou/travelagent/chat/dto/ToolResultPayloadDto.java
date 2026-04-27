package com.xingwuyou.travelagent.chat.dto;

//工具干完活的反馈
public record ToolResultPayloadDto( String toolName,
                                    boolean success,
                                    String summary,
                                    SourceReferenceDto source,
                                    String updatedAt,
                                    String errorMessage) {
}
