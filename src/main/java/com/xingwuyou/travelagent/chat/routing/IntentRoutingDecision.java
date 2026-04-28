package com.xingwuyou.travelagent.chat.routing;

public record IntentRoutingDecision(
        IntentAction action,
        boolean requiresRetrieval,
        String toolName,
        String city,
        String topic,
        Double confidence,
        String reason
) {
    public static IntentRoutingDecision failed(String reason) {
        return new IntentRoutingDecision(
                IntentAction.ROUTING_FAILED,
                false,
                null,
                null,
                null,
                0.0,
                reason
        );
    }
}
