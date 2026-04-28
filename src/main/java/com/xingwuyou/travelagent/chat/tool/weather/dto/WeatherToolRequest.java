package com.xingwuyou.travelagent.chat.tool.weather.dto;

public record WeatherToolRequest( String city,
                                  int dayOffset,
                                  int days,
                                  String timeExpression,
                                  String rawQuestion) {
    public String dayLabel() {
        //首先需要看大数据有没有抽取到
        if (timeExpression != null && !timeExpression.isBlank()) {
            return timeExpression;
        }

        if (days > 1) {
            return "未来%d天".formatted(days);
        }

        return switch (dayOffset) {
            case 1 -> "明天";
            case 2 -> "后天";
            default -> "今天";
        };
    }
}
