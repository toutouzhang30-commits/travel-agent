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
import com.xingwuyou.travelagent.chat.model.ItineraryDay;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

        boolean hasStartDate = requirement != null && requirement.startDate() != null;

        if (!hasStartDate && (plan == null || !plan.needsWeather())) {
            return ItineraryEvidenceBundle.empty();
        }


        WeatherToolRequest request = buildItineraryWeatherRequest(requirement, message);

        if (request == null) {
            request = weatherQueryExtractor.extract(
                    plan.weatherQuestion() == null ? message : plan.weatherQuestion(),
                    state
            );
        }

        WeatherToolResponse response = weatherTool.queryWeather(request);

        return new ItineraryEvidenceBundle(List.of(ToolEvidenceFactory.fromWeather(response)));
    }

    //地图，计划表已经生成了
    public ItineraryEvidenceBundle collectAfterDraft(
            TripRequirement requirement,
            Itinerary draft,
            SessionState state
    ) {
        if (requirement == null || !StringUtils.hasText(requirement.destination())) {
            return ItineraryEvidenceBundle.empty();
        }

        List<RouteCheckItem> items = buildDailyRouteItems(draft);

        if (items.isEmpty()) {
            return ItineraryEvidenceBundle.empty();
        }

        List<ToolEvidence> evidence = new ArrayList<>();

        for (RouteCheckItem item : items) {
            MapsRouteRequest request = new MapsRouteRequest(
                    requirement.destination(),
                    item.origin(),
                    item.destination(),
                    item.travelMode() == null ? TravelMode.TRANSIT : item.travelMode(),
                    null,
                    item.reason()
            );

            MapsRouteResponse response = mapsTool.queryRoute(request);
            evidence.add(ToolEvidenceFactory.fromMaps(response, item));
        }

        return new ItineraryEvidenceBundle(evidence);
    }

    private List<RouteCheckItem> buildDailyRouteItems(Itinerary draft) {
        if (draft == null || draft.days() == null || draft.days().isEmpty()) {
            return List.of();
        }

        List<RouteCheckItem> items = new ArrayList<>();

        for (ItineraryDay day : draft.days()) {
            if (day == null) {
                continue;
            }

            Integer dayNumber = day.dayNumber();

            if (day.morning() != null && day.afternoon() != null) {
                addRouteItem(
                        items,
                        dayNumber,
                        "afternoon",
                        day.morning().activityName(),
                        day.afternoon().activityName(),
                        "第%s天上午到下午的主要移动".formatted(dayNumber == null ? "?" : dayNumber)
                );
            }

            if (day.afternoon() != null && day.evening() != null) {
                addRouteItem(
                        items,
                        dayNumber,
                        "evening",
                        day.afternoon().activityName(),
                        day.evening().activityName(),
                        "第%s天下午到晚上的主要移动".formatted(dayNumber == null ? "?" : dayNumber)
                );
            }
        }

        return List.copyOf(items);
    }

    private void addRouteItem(
            List<RouteCheckItem> items,
            Integer dayNumber,
            String targetSlot,
            String origin,
            String destination,
            String reason
    ) {
        if (!StringUtils.hasText(origin) || !StringUtils.hasText(destination)) {
            return;
        }

        if (origin.equals(destination)) {
            return;
        }

        items.add(new RouteCheckItem(
                dayNumber,
                targetSlot,
                origin,
                destination,
                TravelMode.TRANSIT,
                reason
        ));
    }


    private WeatherToolRequest buildItineraryWeatherRequest(
            TripRequirement requirement,
            String message
    ) {
        if (requirement == null || requirement.startDate() == null || requirement.tripDays() == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = LocalDate.parse(requirement.startDate());

        long offset = ChronoUnit.DAYS.between(today, startDate);
        if (offset < 0 || offset > 6) {
            return null;
        }

        int days = Math.min(requirement.tripDays(), 7 - (int) offset);

        return new WeatherToolRequest(
                requirement.destination(),
                (int) offset,
                days,
                requirement.startDate() + " 开始连续 " + days + " 天",
                message
        );
    }


}
