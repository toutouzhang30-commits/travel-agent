package com.xingwuyou.travelagent.chat.tool.maps.client;

import tools.jackson.databind.JsonNode;
import com.xingwuyou.travelagent.chat.config.AmapProperties;
import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteRequest;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.dto.RouteOption;
import com.xingwuyou.travelagent.chat.tool.maps.dto.TravelMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class AmapRouteClient {
    private final RestClient amapRestClient;
    private final AmapProperties amapProperties;

    public AmapRouteClient(
            @Qualifier("amapRestClient") RestClient amapRestClient,
            AmapProperties amapProperties
    ) {
        this.amapRestClient = amapRestClient;
        this.amapProperties = amapProperties;
    }

    public MapsRouteResponse queryRoute(MapsRouteRequest request) {
        String originLocation = geocode(request.origin(), request.city());
        String destinationLocation = geocode(request.destination(), request.city());

        JsonNode route = switch (request.travelMode() == null ? TravelMode.TRANSIT : request.travelMode()) {
            case WALKING -> queryWalking(originLocation, destinationLocation);
            case DRIVING -> queryDriving(originLocation, destinationLocation);
            case CYCLING -> queryCycling(originLocation, destinationLocation);
            case TRANSIT -> queryTransit(originLocation, destinationLocation, request.city());
        };

        RouteOption option = parseFirstOption(route, request.travelMode());
        String updatedAt = OffsetDateTime.now().toString();

        return new MapsRouteResponse(
                true,
                request.city(),
                request.origin(),
                request.destination(),
                List.of(option),
                SourceReferenceDto.tool(
                        request.city(),
                        "高德地图",
                        "https://lbs.amap.com/api/webservice/guide/api/direction",
                        updatedAt,
                        "高德地图路线规划结果",
                        "MapsTool"
                ),
                updatedAt,
                null
        );
    }

    private String geocode(String address, String city) {
        JsonNode response = amapRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/geocode/geo")
                        .queryParam("key", amapProperties.apiKey())
                        .queryParam("address", address)
                        .queryParam("city", city)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        ensureSuccess(response, "高德地理编码失败");
        JsonNode first = response.path("geocodes").path(0);
        String location = first.path("location").asText(null);
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("高德没有找到地点：" + address);
        }
        return location;
    }

    private JsonNode queryWalking(String origin, String destination) {
        return amapRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/direction/walking")
                        .queryParam("key", amapProperties.apiKey())
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode queryDriving(String origin, String destination) {
        return amapRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/direction/driving")
                        .queryParam("key", amapProperties.apiKey())
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode queryTransit(String origin, String destination, String city) {
        return amapRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/direction/transit/integrated")
                        .queryParam("key", amapProperties.apiKey())
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .queryParam("city", city)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode queryCycling(String origin, String destination) {
        return amapRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v4/direction/bicycling")
                        .queryParam("key", amapProperties.apiKey())
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private RouteOption parseFirstOption(JsonNode response, TravelMode mode) {
        ensureSuccess(response, "高德路线规划失败");

        TravelMode effectiveMode = mode == null ? TravelMode.TRANSIT : mode;
        JsonNode path = effectiveMode == TravelMode.TRANSIT
                ? response.path("route").path("transits").path(0)
                : response.path("route").path("paths").path(0);

        if (path.isMissingNode() || path.isNull()) {
            throw new IllegalStateException("高德没有返回可用路线");
        }

        int distanceMeters = path.path("distance").asInt(0);
        int durationSeconds = path.path("duration").asInt(0);

        return new RouteOption(
                displayModeName(effectiveMode),
                formatDuration(durationSeconds),
                durationSeconds <= 0 ? null : durationSeconds / 60,
                formatDistance(distanceMeters),
                distanceMeters <= 0 ? null : distanceMeters,
                buildInstructionSummary(path, effectiveMode)
        );
    }

    private String buildInstructionSummary(JsonNode path, TravelMode mode) {
        if (mode == TravelMode.TRANSIT) {
            return path.path("segments").path(0).path("bus").path("buslines").path(0).path("name")
                    .asText("按高德推荐公交/地铁方案出行");
        }
        return path.path("steps").path(0).path("instruction")
                .asText("按高德推荐路线出行");
    }

    private String displayModeName(TravelMode mode) {
        return switch (mode) {
            case WALKING -> "步行";
            case TRANSIT -> "公交/地铁";
            case DRIVING -> "驾车/打车";
            case CYCLING -> "骑行";
        };
    }

    private void ensureSuccess(JsonNode response, String message) {
        if (response == null) {
            throw new IllegalStateException(message + "：响应为空");
        }
        String status = response.path("status").asText();
        if (!"1".equals(status)) {
            String info = response.path("info").asText("未知错误");
            throw new IllegalStateException(message + "：" + info);
        }
    }

    private String formatDistance(int meters) {
        if (meters <= 0) return "距离未知";
        if (meters >= 1000) return "%.1f 公里".formatted(meters / 1000.0);
        return meters + " 米";
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "耗时未知";
        int minutes = Math.max(1, seconds / 60);
        if (minutes >= 60) return "%d 小时 %d 分钟".formatted(minutes / 60, minutes % 60);
        return minutes + " 分钟";
    }
}
