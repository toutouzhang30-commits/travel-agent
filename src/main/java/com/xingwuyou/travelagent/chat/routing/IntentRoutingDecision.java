package com.xingwuyou.travelagent.chat.routing;

public record IntentRoutingDecision(
        IntentAction action,
        RouteOutputMode outputMode,
        boolean requiresRetrieval,
        String toolName,
        String city,
        String topic,
        Double confidence,
        String reason
) {
    public IntentRoutingDecision {
        if (outputMode == null) {
            outputMode = defaultOutputMode(action);
        }
    }

    private static RouteOutputMode defaultOutputMode(IntentAction action) {
        if (action == null) return RouteOutputMode.ROUTING_FAILED;
        return switch (action) {
            case COLLECT_REQUIREMENT -> RouteOutputMode.FOLLOW_UP;
            case KNOWLEDGE_QA -> RouteOutputMode.KNOWLEDGE_ANSWER;
            case GENERATE_ITINERARY -> RouteOutputMode.ITINERARY;
            case MODIFY_ITINERARY -> RouteOutputMode.ITINERARY_UPDATE;
            case WEATHER_TOOL, MAPS_TOOL, PRICING_TOOL -> RouteOutputMode.TOOL_RESULT;
            case ROUTING_FAILED -> RouteOutputMode.ROUTING_FAILED;
        };
    }

    public static IntentRoutingDecision failed(String reason) {
        return new IntentRoutingDecision(
                IntentAction.ROUTING_FAILED,
                RouteOutputMode.ROUTING_FAILED,
                false,
                null,
                null,
                null,
                0.0,
                reason
        );
    }
}
