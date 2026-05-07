package com.xingwuyou.travelagent.chat.agent.evidence;

import com.xingwuyou.travelagent.chat.agent.evidence.dto.ItineraryEvidencePlan;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.session.model.SessionState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ItineraryEvidencePlanner {
    private final ChatClient chatClient;

    public ItineraryEvidencePlanner(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("""
                你是行程生成前的证据规划器。
                判断生成行程前是否需要天气证据、生成草稿后是否需要地图路线校验。
                只输出 JSON。
                """).build();
    }

    public ItineraryEvidencePlan plan(String message, TripRequirement requirement, SessionState state) {
        return chatClient.prompt()
                .user("""
                        用户问题：%s
                        目的地：%s
                        天数：%s
                        当前记忆：%s
                        """.formatted(message, requirement.destination(), requirement.tripDays(), state.memory()))
                .call()
                .entity(ItineraryEvidencePlan.class);
    }
}
