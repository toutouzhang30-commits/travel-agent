package com.xingwuyou.travelagent.chat.agent.evidence;

import com.xingwuyou.travelagent.chat.agent.evidence.dto.RouteCheckPlan;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;


//路程检查
@Component
public class RouteCheckPlanner {
    private final ChatClient chatClient;

    public RouteCheckPlanner(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("""
                你是路线校验规划器。
                从 itinerary 中选出最需要地图校验的跨区域移动，不超过 maxRouteChecks 条。
                只输出 JSON。
                """).build();
    }

    public RouteCheckPlan plan(TripRequirement requirement, Itinerary itinerary, int maxRouteChecks) {
        return chatClient.prompt()
                .user("""
                        城市：%s
                        行程：%s
                        最多校验：%s
                        """.formatted(requirement.destination(), itinerary, maxRouteChecks))
                .call()
                .entity(RouteCheckPlan.class);
    }
}
