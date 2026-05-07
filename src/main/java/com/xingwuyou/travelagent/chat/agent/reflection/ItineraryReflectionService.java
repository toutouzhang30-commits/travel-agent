package com.xingwuyou.travelagent.chat.agent.reflection;

import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

//负责检查：天气是否被考虑、路线是否太远、节奏是否太满、是否需要最多一次局部修正。
@Component
public class ItineraryReflectionService {
    private final ChatClient chatClient;

    public ItineraryReflectionService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("""
                你是行程质量检查器。
                检查 itinerary 是否符合用户需求、天气证据、地图路线耗时和节奏约束。
                只有当成功的 MapsTool 证据存在，且 itinerary 中对应路线的 routeRecommendation 为空时，requiresRevision 才可以为 true。
                如果 MapsTool 证据是失败状态，不能要求补 routeRecommendation，也不能把失败原因写入 itinerary。
                revisionInstruction 要求只补充 routeRecommendation，不要重写整份行程。
                最多建议一次局部修正。
                只输出 JSON，不要输出推理过程。
                """).build();
    }

    public ItineraryReflectionResult review(
            TripRequirement requirement,
            Itinerary itinerary,
            String ragContext,
            String toolEvidenceContext
    ) {
        return chatClient.prompt()
                .user("""
                        需求：
                        %s

                        当前行程：
                        %s

                        旅游知识证据：
                        %s

                        实时工具证据：
                        %s
                        """.formatted(
                        requirement,
                        itinerary,
                        ragContext == null ? "" : ragContext,
                        toolEvidenceContext == null ? "" : toolEvidenceContext
                ))
                .call()
                .entity(ItineraryReflectionResult.class);
    }
}
