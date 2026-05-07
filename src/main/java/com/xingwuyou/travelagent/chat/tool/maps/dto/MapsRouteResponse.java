package com.xingwuyou.travelagent.chat.tool.maps.dto;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;

import java.util.List;

public record MapsRouteResponse(boolean success,
                                String city,
                                String origin,
                                String destination,
                                List<RouteOption> options,
                                SourceReferenceDto source,
                                String updatedAt,
                                String errorMessage) {
}
