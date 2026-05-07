package com.xingwuyou.travelagent.chat.tool.maps.common;

import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteRequest;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.dto.RouteOption;
import org.springframework.stereotype.Component;

@Component
public class MapsAnswerGenerator {
    public String buildMapsAnswer(MapsRouteRequest request, MapsRouteResponse response) {
        if (response == null) {
            return "我没能拿到地图工具结果，所以不能可靠回答这条路线的实时耗时。";
        }

        if (!response.success()) {
            return "这属于实时路线问题，但地图工具调用失败：" + response.errorMessage();
        }

        if (response.options() == null || response.options().isEmpty()) {
            return "高德地图没有返回可用路线，我现在不能可靠判断这段路的距离和耗时。";
        }

        RouteOption option = response.options().get(0);

        return """
                根据高德地图，%s 到 %s 可优先按%s出行。
                预计距离：%s。
                预计耗时：%s。
                路线摘要：%s。
                更新时间：%s。
                """.formatted(
                response.origin(),
                response.destination(),
                option.mode(),
                option.distanceText(),
                option.durationText(),
                option.instructionSummary(),
                response.updatedAt()
        );
    }
}
