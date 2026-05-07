package com.xingwuyou.travelagent.chat.agent.evidence;

import com.xingwuyou.travelagent.chat.agent.evidence.dto.ItineraryEvidenceBundle;
import com.xingwuyou.travelagent.chat.agent.evidence.dto.ItineraryEvidencePlan;
import com.xingwuyou.travelagent.chat.agent.evidence.dto.RouteCheckItem;
import com.xingwuyou.travelagent.chat.agent.evidence.dto.RouteCheckPlan;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.session.model.SessionState;
import com.xingwuyou.travelagent.chat.session.model.ToolEvidence;
import com.xingwuyou.travelagent.chat.tool.maps.MapsTool;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteRequest;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteResponse;
import com.xingwuyou.travelagent.chat.tool.maps.dto.TravelMode;
import com.xingwuyou.travelagent.chat.tool.weather.WeatherTool;
import com.xingwuyou.travelagent.chat.tool.weather.common.WeatherQueryExtractor;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

//生成行程前调用天气，生成草稿后调用地图工具校验路线
@Component
public class ItineraryEvidenceCollector {
    private final ItineraryEvidencePlanner evidencePlanner;
    private final WeatherQueryExtractor weatherQueryExtractor;
    private final WeatherTool weatherTool;
    private final RouteCheckPlanner routeCheckPlanner;
    private final MapsTool mapsTool;

    public ItineraryEvidenceCollector(ItineraryEvidencePlanner evidencePlanner,
                                      WeatherQueryExtractor weatherQueryExtractor,
                                      WeatherTool weatherTool,
                                      RouteCheckPlanner routeCheckPlanner,
                                      MapsTool mapsTool){
        this.evidencePlanner=evidencePlanner;
        this.weatherQueryExtractor=weatherQueryExtractor;
        this.weatherTool=weatherTool;
        this.routeCheckPlanner=routeCheckPlanner;
        this.mapsTool=mapsTool;
    }
    //天气
    public ItineraryEvidenceBundle collectBeforeGeneration(
            String message,
            TripRequirement requirement,
            SessionState state
    ) {
        ItineraryEvidencePlan plan = evidencePlanner.plan(message, requirement, state);

        if (plan == null || !plan.needsWeather()) {
            return ItineraryEvidenceBundle.empty();
        }

        WeatherToolRequest request = weatherQueryExtractor.extract(
                plan.weatherQuestion() == null ? message : plan.weatherQuestion(),
                state
        );

        WeatherToolResponse response = weatherTool.queryWeather(request);

        return new ItineraryEvidenceBundle(List.of(ToolEvidenceFactory.fromWeather(response)));
    }

    //地图，计划表已经生成了
    public ItineraryEvidenceBundle collectAfterDraft(
            TripRequirement requirement,
            Itinerary draft,
            SessionState state
    ) {
        RouteCheckPlan plan = routeCheckPlanner.plan(requirement, draft, 4);

        if (plan == null || plan.items() == null || plan.items().isEmpty()) {
            return ItineraryEvidenceBundle.empty();
        }

        List<ToolEvidence> evidence = new ArrayList<>();

        for (RouteCheckItem item : plan.items()) {
            MapsRouteRequest request = new MapsRouteRequest(
                    requirement.destination(),
                    item.origin(),
                    item.destination(),
                    item.travelMode() == null ? TravelMode.TRANSIT : item.travelMode(),
                    null,
                    item.reason()
            );

            MapsRouteResponse response = mapsTool.queryRoute(request);
            evidence.add(ToolEvidenceFactory.fromMaps(response));
        }

        return new ItineraryEvidenceBundle(evidence);
    }


}
