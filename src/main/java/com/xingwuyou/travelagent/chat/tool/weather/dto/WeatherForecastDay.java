package com.xingwuyou.travelagent.chat.tool.weather.dto;

//新增单日预报
public record WeatherForecastDay( int dayOffset,
                                  String date,
                                  String dayLabel,
                                  String dayWeather,
                                  String nightWeather,
                                  String dayTemp,
                                  String nightTemp,
                                  String dayWind,
                                  String nightWind,
                                  String advice) {
}
