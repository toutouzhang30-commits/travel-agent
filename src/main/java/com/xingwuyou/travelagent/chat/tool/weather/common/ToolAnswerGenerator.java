package com.xingwuyou.travelagent.chat.tool.weather.common;

import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import org.springframework.stereotype.Component;

@Component
public class ToolAnswerGenerator {
    //返回给前端的？
    //返回给前端，需要多天的天气
    public String buildWeatherAnswer(WeatherToolRequest request, WeatherToolResponse response) {
        if (!response.success()) {
            String cityText = request.city() == null ? "当前目的地" : request.city();
            return "我尝试查询%s%s的天气，但这次没有成功。%s"
                    .formatted(cityText, request.dayLabel(), response.errorMessage());
        }

        if (response.forecasts() != null && response.forecasts().size() > 1) {
            StringBuilder builder = new StringBuilder();
            builder.append("%s%s天气如下：\n".formatted(response.city(), request.dayLabel()));

            for (var day : response.forecasts()) {
                builder.append("""
                        - %s（%s）：白天%s，%s℃；夜间%s，%s℃。建议：%s
                        """.formatted(
                        day.dayLabel(),
                        day.date(),
                        day.dayWeather(),
                        day.dayTemp(),
                        day.nightWeather(),
                        day.nightTemp(),
                        day.advice()
                ));
            }

            builder.append("\n数据来源：")
                    .append(response.source() == null ? "未知来源" : response.source().sourceName())
                    .append("\n更新时间：")
                    .append(response.updatedAt() == null ? "未知" : response.updatedAt());

            return builder.toString();
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
