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
        你的任务是从 itinerary 中生成需要地图校验的路线列表。
        必须优先为每一天生成 morning.activityName 到 afternoon.activityName 的路线检查项。
        如果当天 evening.activityName 与 afternoon.activityName 距离可能较远，也可以额外生成 afternoon 到 evening 的路线检查项。
        每条路线必须包含 dayNumber、targetSlot、origin、destination、travelMode、reason。
        targetSlot 表示这条 routeRecommendation 应写入 itinerary 的哪个时段：
        - morning 到 afternoon 的路线，targetSlot = "afternoon"
        - afternoon 到 evening 的路线，targetSlot = "evening"
        不要只选择第一天；如果 tripDays = 2，至少应包含第 1 天和第 2 天的 morning -> afternoon。
        输出数量不超过 maxRouteChecks。
        只输出 JSON。
        """).build();

    }

    public RouteCheckPlan plan(TripRequirement requirement, Itinerary itinerary, int maxRouteChecks) {
        return chatClient.prompt()
                .user("""
        城市：%s
        行程：%s
        最多校验：%s

        请返回 RouteCheckPlan JSON，格式如下：
        {
          "items": [
            {
              "dayNumber": 1,
              "targetSlot": "afternoon",
              "origin": "上午地点",
              "destination": "下午地点",
              "travelMode": "TRANSIT",
              "reason": "第1天上午到下午的主要移动"
            }
          ]
        }
        """.formatted(requirement.destination(), itinerary, maxRouteChecks))

                .call()
                .entity(RouteCheckPlan.class);
    }
}
