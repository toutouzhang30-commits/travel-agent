package com.xingwuyou.travelagent.chat.agent.reaction;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ToolReActPlannerService {
    private final ChatClient chatClient;

    public ToolReActPlannerService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("""
                你是受控工具修复器。
                只允许输出一次修复决策，不能循环。
                """).build();
    }

    public ToolRepairDecision decide(String toolName, String userMessage, String requestSnapshot, String errorMessage, String memoryContext) {
        return chatClient.prompt()
                .user("""
                        工具：%s
                        用户原话：%s
                        请求快照：%s
                        失败原因：%s
                        Working Memory：%s
                        """.formatted(toolName, userMessage, requestSnapshot, errorMessage, memoryContext == null ? "" : memoryContext))
                .call()
                .entity(ToolRepairDecision.class);
    }
}
