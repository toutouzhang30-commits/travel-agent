package com.xingwuyou.travelagent.chat.tool.maps.dto;

public record RouteOption(String mode,
                          String durationText,
                          Integer durationMinutes,
                          String distanceText,
                          Integer distanceMeters,
                          String instructionSummary) {
}
