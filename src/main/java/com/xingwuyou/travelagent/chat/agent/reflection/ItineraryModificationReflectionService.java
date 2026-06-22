package com.xingwuyou.travelagent.chat.agent.reflection;

import com.xingwuyou.travelagent.chat.model.Itinerary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ItineraryModificationReflectionService {
    private final ChatClient chatClient;

    public ItineraryModificationReflectionService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("""
                你是行程修改反思器。
                重点检查：用户要求是否真的被局部落实、没要求改的天数/时段是否保持不变。
                只输出 JSON。
                """).build();
    }

    public ItineraryModificationReflectionResult review(String instruction, Itinerary before, Itinerary after, String memoryContext) {
        return chatClient.prompt()
                .user("""
                        修改指令：%s
                        修改前：%s
                        修改后：%s
                        Working Memory：%s
                        """.formatted(instruction, before, after, memoryContext == null ? "" : memoryContext))
                .call()
                .entity(ItineraryModificationReflectionResult.class);
    }
}
