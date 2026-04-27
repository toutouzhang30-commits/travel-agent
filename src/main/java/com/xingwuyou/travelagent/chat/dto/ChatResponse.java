package com.xingwuyou.travelagent.chat.dto;

import com.xingwuyou.travelagent.chat.model.Itinerary;

import java.util.List;

public record ChatResponse(String sessionId,
                           ChatResponseType type,
                           String message,
                           TripRequirement requirement,
                           List<String> missingFields,
                           String followUpQuestion,
                           Itinerary itinerary,
                           List<SourceReferenceDto> sources) {
}
