package com.xingwuyou.travelagent.chat.agent.reflection;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeReflectionService {
    private final ChatClient chatClient;

    public KnowledgeReflectionService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("""
                你是知识问答反思器。
                检查回答是否过度确定、是否证据不足、是否需要改成不确定表达。
                只输出 JSON。
                """).build();
    }

    public KnowledgeReflectionResult review(String question, String answer, String ragContext, String memoryContext) {
        return chatClient.prompt()
                .user("""
                        问题：%s
                        回答：%s
                        检索证据：%s
                        Working Memory：%s
                        """.formatted(question, answer, ragContext == null ? "" : ragContext, memoryContext == null ? "" : memoryContext))
                .call()
                .entity(KnowledgeReflectionResult.class);
    }
}
