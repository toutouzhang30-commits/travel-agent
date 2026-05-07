package com.xingwuyou.travelagent.chat.tool.maps;

import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteRequest;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.service.MapsToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MapsTool {
    private final MapsToolService mapsToolService;

    public MapsTool(MapsToolService mapsToolService) {
        this.mapsToolService = mapsToolService;
    }

    @Tool(description = """
            查询两个地点之间的路线、距离和交通耗时。
            只用于实时路线、交通方式、距离、耗时问题。
            不用于回答旅游玩法、区域推荐或静态攻略问题。
            """)
    public MapsRouteResponse queryRoute(
            @ToolParam(description = "路线查询结构化参数，包含城市、起点、终点、交通方式、时间表达和原始问题。")
            MapsRouteRequest request
    ) {
        return mapsToolService.execute(request);
    }
}
