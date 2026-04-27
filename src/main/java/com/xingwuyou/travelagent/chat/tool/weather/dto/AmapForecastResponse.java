package com.xingwuyou.travelagent.chat.tool.weather.dto;

import java.util.List;

public record AmapForecastResponse( String status,
                                    String count,
                                    String info,
                                    String infocode,
                                    List<Forecast> forecasts) {
    public record Forecast(
            String city,
            String adcode,
            String province,
            String reporttime,
            List<Cast> casts
    ) {
    }

    public record Cast(
            String date,
            String week,
            String dayweather,
            String nightweather,
            String daytemp,
            String nighttemp,
            String daywind,
            String nightwind,
            String daypower,
            String nightpower
    ) {
    }
}
