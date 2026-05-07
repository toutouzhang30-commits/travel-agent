package com.xingwuyou.travelagent.chat.component;

import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

//计划生成，chatclient
//第一次的计划生成
@Component
public class ItineraryGenerator {
    private final ChatClient chatClient;

    public ItineraryGenerator(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        你是一个旅行行程生成助手。
                        你必须根据用户需求、旅游知识证据和实时工具证据返回 Itinerary JSON。

                        严格遵守：
                        1. 只输出纯 JSON，不要 Markdown，不要解释。
                        2. 不要编造天气、路线耗时、路线距离、实时交通信息。
                        3. 天气只能来自 WeatherTool 证据。
                        4. 路线只能来自 MapsTool 证据。
                        5. 输出 JSON 必须匹配后端 Itinerary / ItineraryDay / ItinerarySlot 结构。
                    """)
                .build();
    }


    public Itinerary generate(TripRequirement requirement, String ragContext, String toolEvidenceContext) {
        return chatClient.prompt()
                .user("""
                    请根据以下需求生成 Itinerary JSON。

                    旅行需求：
                    - destination: %s
                    - tripDays: %s
                    - startDate: %s
                    - budget: %s
                    - pacePreference: %s
                    - interests: %s

                    旅游知识证据：
                    %s

                    实时工具证据：
                    %s

                    生成要求：
                    1. 只输出 JSON，不要 Markdown，不要解释。
                    2. JSON 必须匹配 Itinerary 结构：
                       {
                         "destination": "...",
                         "tripDays": 2,
                         "days": [
                           {
                             "dayNumber": 1,
                             "date": "yyyy-MM-dd 或 null",
                             "weatherSummary": "天气摘要或 null",
                             "morning": {
                               "activityName": "...",
                               "reason": "...",
                               "routeRecommendation": null,
                               "budgetNote": "..."
                             },
                             "afternoon": {
                               "activityName": "...",
                               "reason": "...",
                               "routeRecommendation": "...",
                               "budgetNote": "..."
                             },
                             "evening": {
                               "activityName": "...",
                               "reason": "...",
                               "routeRecommendation": "...或 null",
                               "budgetNote": "..."
                             }
                           }
                         ]
                       }

                    日期规则：
                    1. 如果 startDate 不为 null：
                       - 第 1 天 date = startDate。
                       - 第 2 天 date = startDate + 1 天。
                       - 第 N 天 date = startDate + N - 1 天。
                    2. 如果 startDate 为 null：
                       - 每一天 date = null。
                    
                    基础规则：
                    1. 只有 destination 和 tripDays 是必须字段。
                    2. 如果 budget 为 null 或未提供，按大众低成本方案安排，并在 budgetNote 中写“预算未提供，优先安排低成本或免费活动”。
                    3. 如果 pacePreference 为 null 或未提供，默认按适中节奏安排。
                    4. 如果 interests 为空，默认安排首次到访城市时最常见的经典景点、城市漫步和顺路体验。
                    5. 如果 startDate 为 null，每一天 date = null。

                    天气规则：
                    1. weatherSummary 只能来自成功的 WeatherTool 证据。
                    2. 如果 WeatherTool 证据包含对应日期天气，请写入当天 weatherSummary。
                    3. 如果没有对应日期天气证据，weatherSummary = null。
                    4. 不要编造天气。
                    5. 不要输出“整体天气较稳定，可按原计划安排室外活动”。
                    
                    路线规则：
                    1. routeRecommendation 只能来自成功的 MapsTool 证据。
                    2. MapsTool 证据包含 dayNumber 和 targetSlot。
                    3. targetSlot=afternoon 表示 morning -> afternoon 的路线，只能写入对应 day 的 afternoon.routeRecommendation。
                    4. targetSlot=evening 表示 afternoon -> evening 的路线，只能写入对应 day 的 evening.routeRecommendation。
                    5. routeRecommendation 必须包含推荐交通工具、预计时间、路程距离。
                    6. 没有对应 dayNumber + targetSlot 的成功 MapsTool 证据时，routeRecommendation = null。
                    7. 不要把地图失败、路线未确认、建议再次查询等兜底文案写入 routeRecommendation。

                    安排规则：
                    1. days 数量必须等于 tripDays。
                    2. 每一天必须包含 morning / afternoon / evening。
                    3. 不要把地图证据显示耗时过长的两个点安排在同一个半天。
                    4. 行程要符合预算、节奏和兴趣偏好。
                    """.formatted(
                        requirement.destination(),
                        requirement.tripDays(),
                        requirement.startDate(),
                        requirement.budget(),
                        requirement.pacePreference(),
                        requirement.interests(),
                        ragContext == null ? "" : ragContext,
                        toolEvidenceContext == null ? "" : toolEvidenceContext
                ))
                .call()
                .entity(Itinerary.class);
    }

}
