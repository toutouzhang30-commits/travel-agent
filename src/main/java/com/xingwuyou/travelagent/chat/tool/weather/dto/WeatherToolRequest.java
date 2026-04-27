package com.xingwuyou.travelagent.chat.tool.weather.dto;

public record WeatherToolRequest( String city,
                                  int dayOffset,
                                  String rawQuestion) {
    public String dayLabel() {
        //从ai识别的离当前几天变成人类常用语言
        return switch (dayOffset) {
            case 1 -> "明天";
            case 2 -> "后天";
            default -> "今天";
        };
    }
}
