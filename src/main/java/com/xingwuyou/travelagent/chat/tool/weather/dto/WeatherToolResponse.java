package com.xingwuyou.travelagent.chat.tool.weather.dto;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;

import java.util.List;

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
                                  List<WeatherForecastDay> forecasts,
                                  SourceReferenceDto source,
                                  String updatedAt,
                                  String errorMessage) {
}
