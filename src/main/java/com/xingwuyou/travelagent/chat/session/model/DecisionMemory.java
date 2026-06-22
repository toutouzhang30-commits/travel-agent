package com.xingwuyou.travelagent.chat.session.model;

public record DecisionMemory(String action,
                             String outputMode,
                             String destination,
                             String topic,
                             String summary,
                             String createdAt
) {
}
