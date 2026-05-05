package com.xingwuyou.travelagent.chat.tool.weather.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;


//为什么要给参数加description
//将模糊的自然语言指令转化为了确定性的程序输出
//约束模型行为，防止产生幻觉
public record WeatherToolRequest(
        @JsonPropertyDescription("城市名，例如 北京、上海、杭州。不能包含省略语。")
        String city,

        @JsonPropertyDescription("从今天开始的日期偏移：今天=0，明天=1，后天=2。")
        int dayOffset,

        @JsonPropertyDescription("连续查询天数。单日查询为 1，未来一周为 7，最大不超过 7。")
        int days,

        @JsonPropertyDescription("用户原始时间表达，例如 今天、明天、未来三天、近一周、周末。")
        String timeExpression,

        @JsonPropertyDescription("用户原始问题。")
        String rawQuestion
) {
    public String dayLabel() {
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
