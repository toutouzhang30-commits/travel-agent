package com.xingwuyou.travelagent.chat.tool.weather;

import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import com.xingwuyou.travelagent.chat.tool.weather.service.WeatherToolService;
import org.springframework.stereotype.Component;

@Component
public class WeatherTool {
    private final WeatherToolService weatherToolService;

    public WeatherTool(WeatherToolService weatherToolService) {
        this.weatherToolService = weatherToolService;
    }

    public WeatherToolResponse queryWeather(WeatherToolRequest request) {
        return weatherToolService.execute(request);
    }
}
