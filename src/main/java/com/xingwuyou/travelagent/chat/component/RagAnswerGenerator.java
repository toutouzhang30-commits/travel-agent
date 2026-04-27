package com.xingwuyou.travelagent.chat.component;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class RagAnswerGenerator {
    private final ChatClient chatClient;

    public RagAnswerGenerator(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        你是一个旅游知识回答助手。
                        你的任务是基于提供的检索知识回答用户问题。

                        严格遵守以下规则：
                        1. 优先依据补充参考知识回答，不要忽略检索内容。
                        2. 如果参考知识不足，就明确说明信息有限，不要编造。
                        3. 回答用自然语言，不输出 JSON。
                        4. 回答尽量简洁、直接、有帮助。
                        """)
                .build();
    }

    public String answer(String question, String ragContext) {
        return chatClient.prompt()
                .user(u -> u.text("""
                        ### 用户问题
                        {question}

                        ### 补充参考知识
                        {ragContext}

                        请基于以上内容回答用户。
                        """)
                        .param("question", question)
                        .param("ragContext", ragContext == null ? "" : ragContext))
                .call()
                .content();
    }
}
