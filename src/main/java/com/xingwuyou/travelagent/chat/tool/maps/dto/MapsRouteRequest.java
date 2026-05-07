package com.xingwuyou.travelagent.chat.tool.maps.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record MapsRouteRequest(@JsonPropertyDescription("城市名，例如 北京、上海、杭州。")
                               String city,

                               @JsonPropertyDescription("出发地点，可以是景点、酒店、商圈或用户描述的位置。")
                               String origin,

                               @JsonPropertyDescription("目的地点，可以是景点、酒店、商圈或用户描述的位置。")
                               String destination,

                               @JsonPropertyDescription("交通方式：WALKING、TRANSIT、DRIVING、CYCLING。用户未指定时优先 TRANSIT。")
                               TravelMode travelMode,

                               @JsonPropertyDescription("用户原始时间表达，例如 今天下午、明天早上、周末。")
                               String timeExpression,

                               @JsonPropertyDescription("用户原始问题。")
                               String rawQuestion) {
}
