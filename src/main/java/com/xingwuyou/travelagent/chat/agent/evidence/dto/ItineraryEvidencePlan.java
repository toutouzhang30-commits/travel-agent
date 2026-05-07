package com.xingwuyou.travelagent.chat.agent.evidence.dto;

public record ItineraryEvidencePlan(boolean needsWeather,
                                    String weatherQuestion,
                                    boolean needsRouteValidation,
                                    Integer maxRouteChecks,
                                    String reason) {
}
