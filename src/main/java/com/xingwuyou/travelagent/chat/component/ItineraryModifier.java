package com.xingwuyou.travelagent.chat.component;

import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

//修改行程的chatclient
@Component
public class ItineraryModifier {
    private final ChatClient chatClient;

    public ItineraryModifier(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        你是一个旅游行程修改助手。
                        你的任务是基于“已有行程”和“用户修改要求”输出修改后的最小行程 JSON。

                        严格遵守以下规则：
                        1. 只输出纯 JSON，不要 Markdown，不要解释。
                        2. 输出结构必须与当前 itinerary 一致：destination, tripDays, days。
                        3. 每一天必须包含 morning / afternoon / evening。
                        4. 每个时间段必须包含 activityName / reason / budgetNote。
                        5. 优先局部修改，只改与用户要求直接相关的部分。
                        6. 没被用户要求修改的天数和时段，尽量保持原样。
                        7. 如果用户说“第二天太累了”，优先调整第二天，而不是全盘重写。
                        8. 如果用户说“不要博物馆，多安排吃的”，优先替换相关活动。
                        9. 如果用户说“预算再压低一点”，优先把活动改成更省钱的选项，并更新 budgetNote。

                    """)
                .build();
    }

    public Itinerary modify(String message, TripRequirement requirement, Itinerary currentItinerary) {
        String currentItineraryText = currentItinerary == null ? "null" : currentItinerary.toString();

        return chatClient.prompt()
                .user(u -> u.text("""
                    ### 1. 当前旅游需求 (Context)
                    目的地：{dest}
                    时长：{days} 天
                    预算档位：{budget}
                    节奏偏好：{pace}
                    兴趣点：{interests}

                    ### 2. 当前行程方案 (Current State)
                    {itineraryJson}

                    ### 3. 用户修改指令 (Instruction)
                    >> {modMessage} <<

                    请根据指令修改上述行程。
                    注意：
                    - 保持未被要求修改的天数和时段不变。
                    - 确保修改后的逻辑连贯（如：地点间的距离、时间衔接）。
                    - 直接输出修改后的完整 Itinerary JSON 对象。
                    """)
                        .param("dest", requirement.destination())
                        .param("days", requirement.tripDays())
                        .param("budget", requirement.budget())
                        .param("pace", requirement.pacePreference())
                        .param("interests", requirement.interests() == null ? "" : String.join(", ", requirement.interests()))
                        .param("itineraryJson", currentItineraryText)
                        .param("modMessage", message)
                )
                .call()
                .entity(Itinerary.class);
    }
}
