package com.xingwuyou.travelagent.chat.tool.maps.service;

import com.xingwuyou.travelagent.chat.tool.maps.client.AmapRouteClient;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteRequest;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.dto.TravelMode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MapsToolService {
    private final AmapRouteClient amapRouteClient;

    public MapsToolService(AmapRouteClient amapRouteClient) {
        this.amapRouteClient = amapRouteClient;
    }

    public MapsRouteResponse execute(MapsRouteRequest request) {
        if (request == null
                || !StringUtils.hasText(request.city())
                || !StringUtils.hasText(request.origin())
                || !StringUtils.hasText(request.destination())) {
            return failure(request, "路线查询缺少城市、起点或终点。");
        }

        MapsRouteRequest normalized = new MapsRouteRequest(
                request.city(),
                request.origin(),
                request.destination(),
                request.travelMode() == null ? TravelMode.TRANSIT : request.travelMode(),
                request.timeExpression(),
                request.rawQuestion()
        );

        try {
            return amapRouteClient.queryRoute(normalized);
        } catch (Exception ex) {
            return failure(normalized, "路线工具调用失败：" + ex.getMessage());
        }
    }

    private MapsRouteResponse failure(MapsRouteRequest request, String message) {
        return new MapsRouteResponse(
                false,
                request == null ? null : request.city(),
                request == null ? null : request.origin(),
                request == null ? null : request.destination(),
                List.of(),
                null,
                OffsetDateTime.now().toString(),
                message
        );
    }
}
