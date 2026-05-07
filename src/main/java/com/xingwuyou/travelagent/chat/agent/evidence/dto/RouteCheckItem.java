package com.xingwuyou.travelagent.chat.agent.evidence.dto;

import com.xingwuyou.travelagent.chat.tool.maps.dto.TravelMode;

public record RouteCheckItem(String origin,
                             String destination,
                             TravelMode travelMode,
                             String reason) {
}
