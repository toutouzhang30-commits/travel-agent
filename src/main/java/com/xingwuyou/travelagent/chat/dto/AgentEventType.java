package com.xingwuyou.travelagent.chat.dto;

//agent到哪一个步骤了
public enum AgentEventType {
    STATUS,
    RETRIEVAL,
    TOOL_CALL,
    TOOL_RESULT,
    ANSWER,
    ITINERARY,
    DONE,
    ERROR
}
