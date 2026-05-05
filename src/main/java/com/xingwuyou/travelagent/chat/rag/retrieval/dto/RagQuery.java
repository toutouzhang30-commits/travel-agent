package com.xingwuyou.travelagent.chat.rag.retrieval.dto;

//检索请求
public record RagQuery( String userQuestion,
                        String city,
                        String topic,
                        int topK) {
}
