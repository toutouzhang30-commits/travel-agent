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
                    你必须根据用户需求返回最小行程 JSON。
                    严格遵守以下规则：
                    1. 只输出纯 JSON，不要 Markdown，不要解释。
                    2. days 数量必须等于 tripDays。
                    3. 每一天必须包含 morning / afternoon / evening。
                    4. 每个时间段必须包含 activityName / reason / budgetNote。
                    5. 当前阶段不依赖真实景点数据库，可以给出合理的通用活动安排。
                    6. 不要输出来源、交通、天气、注意事项等额外字段。
                    """)
                .build();
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
