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
                    你是一个旅游行程生成助手。
                    你必须根据用户需求、旅游知识证据和实时工具证据返回最小行程 JSON。
                    严格遵守以下规则：
                    1. 只输出纯 JSON，不要 Markdown，不要解释。
                    2. days 数量必须等于 tripDays。
                    3. 每一天必须包含 morning / afternoon / evening。
                    4. 每个时间段必须包含 activityName / reason / budgetNote。
                    5. 如果天气证据显示下雨，优先安排室内、短距离或可替代活动。
                    6. 如果地图证据显示跨区域耗时较长，不要把这些点塞进同一个半天。
                    7. 不要编造未由工具提供的实时天气、路线耗时或距离。
                    """)
                .build();
    }


    public Itinerary generate(TripRequirement requirement, String ragContext, String toolEvidenceContext) {
        return chatClient.prompt()
                .user("""
                  请根据以下需求生成行程 JSON。

                  destination: %s
                  tripDays: %s
                  budget: %s
                  pacePreference: %s
                  interests: %s

                  旅游知识证据：
                  %s

                  实时工具证据：
                  %s
                  """.formatted(
                        requirement.destination(),
                        requirement.tripDays(),
                        requirement.budget(),
                        requirement.pacePreference(),
                        requirement.interests(),
                        ragContext == null ? "" : ragContext,
                        toolEvidenceContext == null ? "" : toolEvidenceContext
                ))
                .call()
                .entity(Itinerary.class);
    }



    public Itinerary generate(TripRequirement requirement, String ragContext) {
        return chatClient.prompt()
                .user("""
                      请根据以下需求生成最小行程 JSON：

                      destination: %s
                      tripDays: %s
                      budget: %s
                      pacePreference: %s
                      interests: %s

                      补充参考知识：
                      %s
                      """.formatted(
                        requirement.destination(),
                        requirement.tripDays(),
                        requirement.budget(),
                        requirement.pacePreference(),
                        requirement.interests(),
                        ragContext == null ? "" : ragContext
                ))
                .call()
                .entity(Itinerary.class);
    }
}
