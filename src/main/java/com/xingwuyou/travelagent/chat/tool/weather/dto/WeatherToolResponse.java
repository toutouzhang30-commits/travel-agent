package com.xingwuyou.travelagent.chat.tool.weather.dto;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;

public record WeatherToolResponse(boolean success,
                                  String city,
                                  int dayOffset,
                                  String targetDate,
                                  String dayWeather,
                                  String nightWeather,
                                  String dayTemp,
                                  String nightTemp,
                                  String dayWind,
                                  String nightWind,
                                  String advice,
                                  SourceReferenceDto source,
                                  String updatedAt,
                                  String errorMessage) {
}
