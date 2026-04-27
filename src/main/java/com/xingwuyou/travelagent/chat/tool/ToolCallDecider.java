package com.xingwuyou.travelagent.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class ToolCallDecider {
    //对词语进行判断决定需不需要使用工具
    private static final List<String> WEATHER_KEYWORDS = List.of(
            "天气", "气温", "温度", "下雨", "有雨", "晴", "阴", "刮风"
    );

    private static final List<String> WEATHER_TIME_KEYWORDS = List.of(
            "今天", "明天", "后天", "现在", "今日", "明日", "本周"
    );

    private static final List<String> MAPS_KEYWORDS = List.of(
            "多久", "多远", "怎么走", "路线", "耗时", "交通", "打车", "地铁", "公交"
    );

    private static final List<String> PRICING_KEYWORDS = List.of(
            "门票", "票价", "价格", "多少钱", "余票", "还有票"
    );

    public boolean shouldUseWeatherTool(String message){
        if(!StringUtils.hasText(message)){
            return false;
        }
        boolean hasWeatherKeyword = containsAny(message, WEATHER_KEYWORDS);
        boolean hasTimeKeyword = containsAny(message, WEATHER_TIME_KEYWORDS);
        boolean asksRainPlan = message.contains("下雨怎么办") || message.contains("如果下雨");

        return (hasWeatherKeyword && hasTimeKeyword) || asksRainPlan;
    }

    public boolean shouldUseMapsTool(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return containsAny(message, MAPS_KEYWORDS) && message.contains("到");
    }

    public boolean shouldUsePricingTool(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return containsAny(message, PRICING_KEYWORDS);
    }

    private boolean containsAny(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }
}
