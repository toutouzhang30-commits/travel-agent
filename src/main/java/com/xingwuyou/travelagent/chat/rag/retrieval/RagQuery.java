package com.xingwuyou.travelagent.chat.rag.retrieval;

//检索请求
public record RagQuery( String userQuestion,
                        String city,
                        String topic,
                        int topK) {
}
