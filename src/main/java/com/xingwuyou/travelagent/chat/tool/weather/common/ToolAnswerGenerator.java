package com.xingwuyou.travelagent.chat.tool.weather.common;

import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import org.springframework.stereotype.Component;

@Component
public class ToolAnswerGenerator {
    //返回给前端的？
    public String buildWeatherAnswer(WeatherToolRequest request, WeatherToolResponse response) {
        if (!response.success()) {
            String cityText = request.city() == null ? "当前目的地" : request.city();
            return "我尝试查询%s%s的天气，但这次没有成功。%s"
                    .formatted(cityText, request.dayLabel(), response.errorMessage());
        }

        return """
                %s%s（%s）天气如下：
                - 白天：%s，%s℃
                - 夜间：%s，%s℃
                - 建议：%s

                数据来源：%s
                更新时间：%s
                """.formatted(
                response.city(),
                request.dayLabel(),
                response.targetDate(),
                response.dayWeather(),
                response.dayTemp(),
                response.nightWeather(),
                response.nightTemp(),
                response.advice(),
                response.source() == null ? "未知来源" : response.source().sourceName(),
                response.updatedAt() == null ? "未知" : response.updatedAt()
        );
    }
}
