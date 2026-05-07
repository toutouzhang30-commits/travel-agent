package com.xingwuyou.travelagent.chat.agent.evidence;


import com.xingwuyou.travelagent.chat.session.model.ToolEvidence;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.dto.RouteOption;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;

//把 WeatherToolResponse / MapsRouteResponse 转成统一 ToolEvidence
public class ToolEvidenceFactory {
    public static ToolEvidence fromWeather(WeatherToolResponse response) {
        if (response == null) {
            return new ToolEvidence("WeatherTool", false, null, "天气工具没有返回结果", null, null, "响应为空");
        }

        String sourceName = response.source() == null ? null : response.source().sourceName();

        String summary = response.success()
                ? "%s %s：白天%s，夜间%s，%s℃/%s℃，建议：%s".formatted(
                response.city(),
                response.targetDate(),
                response.dayWeather(),
                response.nightWeather(),
                response.dayTemp(),
                response.nightTemp(),
                response.advice()
        )
                : "天气工具失败：" + response.errorMessage();

        return new ToolEvidence(
                "WeatherTool",
                response.success(),
                response.city(),
                summary,
                sourceName,
                response.updatedAt(),
                response.errorMessage()
        );
    }

    public static ToolEvidence fromMaps(MapsRouteResponse response) {
        if (response == null) {
            return new ToolEvidence("MapsTool", false, null, "地图工具没有返回结果", null, null, "响应为空");
        }

        String sourceName = response.source() == null ? null : response.source().sourceName();

        String summary;
        if (response.success() && response.options() != null && !response.options().isEmpty()) {
            RouteOption option = response.options().get(0);
            summary = "%s 到 %s：%s，约 %s，方式：%s，摘要：%s".formatted(
                    response.origin(),
                    response.destination(),
                    option.distanceText(),
                    option.durationText(),
                    option.mode(),
                    option.instructionSummary()
            );
        } else {
            summary = "地图工具失败：" + response.errorMessage();
        }

        return new ToolEvidence(
                "MapsTool",
                response.success(),
                response.city(),
                summary,
                sourceName,
                response.updatedAt(),
                response.errorMessage()
        );
    }
}
