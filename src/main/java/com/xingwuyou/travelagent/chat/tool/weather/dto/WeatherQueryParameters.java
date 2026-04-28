package com.xingwuyou.travelagent.chat.tool.weather.dto;

//结果DTO
public record WeatherQueryParameters(String city,
                                     Integer dayOffset,
                                     Integer days,
                                     String timeExpression,
                                     String reason) {
}
