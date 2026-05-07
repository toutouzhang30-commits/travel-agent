package com.xingwuyou.travelagent.chat.agent.evidence;

import com.xingwuyou.travelagent.chat.agent.evidence.dto.RouteCheckItem;
import com.xingwuyou.travelagent.chat.session.model.ToolEvidence;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.dto.RouteOption;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherForecastDay;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;

import java.util.stream.Collectors;

// 把 WeatherToolResponse / MapsRouteResponse 转成统一 ToolEvidence
public class ToolEvidenceFactory {

    public static ToolEvidence fromWeather(WeatherToolResponse response) {
        if (response == null) {
            return new ToolEvidence(
                    "WeatherTool",
                    false,
                    null,
                    "天气工具没有返回结果",
                    null,
                    null,
                    "响应为空",
                    null,
                    null,
                    null,
                    null
            );
        }

        String sourceName = response.source() == null ? null : response.source().sourceName();
        String summary = buildWeatherSummary(response);

        return new ToolEvidence(
                "WeatherTool",
                response.success(),
                response.city(),
                summary,
                sourceName,
                response.updatedAt(),
                response.errorMessage(),
                null,
                null,
                null,
                null
        );
    }

    public static ToolEvidence fromMaps(MapsRouteResponse response) {
        if (response == null) {
            return new ToolEvidence(
                    "MapsTool",
                    false,
                    null,
                    "地图工具没有返回结果",
                    null,
                    null,
                    "响应为空",
                    null,
                    null,
                    null,
                    null
            );
        }

        String sourceName = response.source() == null ? null : response.source().sourceName();
        String summary = buildMapsSummary(response);

        return new ToolEvidence(
                "MapsTool",
                response.success(),
                response.city(),
                summary,
                sourceName,
                response.updatedAt(),
                response.errorMessage(),
                null,
                null,
                response.origin(),
                response.destination()
        );
    }

    public static ToolEvidence fromMaps(MapsRouteResponse response, RouteCheckItem item) {
        ToolEvidence base = fromMaps(response);

        return new ToolEvidence(
                base.toolName(),
                base.success(),
                base.city(),
                base.summary(),
                base.sourceName(),
                base.updatedAt(),
                base.errorMessage(),
                item == null ? null : item.dayNumber(),
                item == null ? null : item.targetSlot(),
                item == null ? base.origin() : item.origin(),
                item == null ? base.destination() : item.destination()
        );
    }

    private static String buildWeatherSummary(WeatherToolResponse response) {
        if (!response.success()) {
            return "天气工具失败：" + response.errorMessage();
        }

        if (response.forecasts() != null && !response.forecasts().isEmpty()) {
            return response.forecasts().stream()
                    .map(ToolEvidenceFactory::formatForecastDay)
                    .collect(Collectors.joining("；"));
        }

        return "%s %s：白天%s，夜间%s，%s℃/%s℃".formatted(
                response.city(),
                response.targetDate(),
                response.dayWeather(),
                response.nightWeather(),
                response.dayTemp(),
                response.nightTemp()
        );
    }

    private static String formatForecastDay(WeatherForecastDay day) {
        return "%s（%s）：白天%s，夜间%s，%s℃/%s℃".formatted(
                day.dayLabel(),
                day.date(),
                day.dayWeather(),
                day.nightWeather(),
                day.dayTemp(),
                day.nightTemp()
        );
    }

    private static String buildMapsSummary(MapsRouteResponse response) {
        if (!response.success()) {
            return "地图工具失败：" + response.errorMessage();
        }

        if (response.options() == null || response.options().isEmpty()) {
            return "地图工具没有返回可用路线";
        }

        RouteOption option = response.options().get(0);

        return "%s 到 %s：%s，约 %s，方式：%s，摘要：%s".formatted(
                response.origin(),
                response.destination(),
                option.distanceText(),
                option.durationText(),
                option.mode(),
                option.instructionSummary()
        );
    }
}
