package com.xingwuyou.travelagent.chat.dto;

public record AgentEvent(String sessionId,
                         AgentEventType type,
                         String message,
                         ChatResponse payload,
                         ToolCallPayloadDto toolCallPayload,
                         ToolResultPayloadDto toolResultPayload) {

    public static AgentEvent status(String sessionId, String message) {
        return new AgentEvent(sessionId, AgentEventType.STATUS, message, null, null, null);
    }

    public static AgentEvent retrieval(String sessionId, String message, ChatResponse payload) {
        return new AgentEvent(sessionId, AgentEventType.RETRIEVAL, message, payload, null, null);
    }

    public static AgentEvent toolCall(String sessionId, String message, ToolCallPayloadDto payload) {
        return new AgentEvent(sessionId, AgentEventType.TOOL_CALL, message, null, payload, null);
    }

    public static AgentEvent toolResult(String sessionId, String message, ToolResultPayloadDto payload) {
        return new AgentEvent(sessionId, AgentEventType.TOOL_RESULT, message, null, null, payload);
    }

    //回答问题，知识问答
    public static AgentEvent answer(String sessionId, ChatResponse payload) {
        String message = payload.type() == ChatResponseType.FOLLOW_UP
                && payload.followUpQuestion() != null
                && !payload.followUpQuestion().isBlank()
                ? payload.followUpQuestion()
                : payload.message();
        return new AgentEvent(sessionId, AgentEventType.ANSWER, message, payload, null, null);
    }

    //生成计划表
    public static AgentEvent itinerary(String sessionId, ChatResponse payload) {
        return new AgentEvent(sessionId, AgentEventType.ITINERARY, payload.message(), payload, null, null);
    }

    public static AgentEvent done(String sessionId) {
        return new AgentEvent(sessionId, AgentEventType.DONE, "done", null, null, null);
    }

    public static AgentEvent error(String sessionId, String message) {
        return new AgentEvent(sessionId, AgentEventType.ERROR, message, null, null, null);
    }
}
