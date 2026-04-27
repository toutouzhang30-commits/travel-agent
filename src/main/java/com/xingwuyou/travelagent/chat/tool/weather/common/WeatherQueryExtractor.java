package com.xingwuyou.travelagent.chat.tool.weather.common;

import com.xingwuyou.travelagent.chat.session.SessionState;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

//天气参数提取器
@Component
public class WeatherQueryExtractor {
    public WeatherToolRequest extract(String message, SessionState sessionState) {
        //需要先从问题中获取参数
        String city = extractCity(message);

        if (!StringUtils.hasText(city)
                && sessionState != null
                && sessionState.requirement() != null
                && StringUtils.hasText(sessionState.requirement().destination())) {
            city = sessionState.requirement().destination();
        }

        int dayOffset = extractDayOffset(message);
        return new WeatherToolRequest(city, dayOffset, message);

    }

    private String extractCity(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        if (message.contains("北京")) {
            return "北京";
        }
        if (message.contains("上海")) {
            return "上海";
        }
        if (message.contains("杭州")) {
            return "杭州";
        }
        return null;
    }

    private int extractDayOffset(String message) {
        if (!StringUtils.hasText(message)) {
            return 0;
        }
        if (message.contains("后天")) {
            return 2;
        }
        if (message.contains("明天") || message.contains("明日")) {
            return 1;
        }
        return 0;
    }

}