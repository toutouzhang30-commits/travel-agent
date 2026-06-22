package com.xingwuyou.travelagent.chat.session.service;

import com.xingwuyou.travelagent.chat.dto.ChatResponse;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.model.ItineraryDay;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import com.xingwuyou.travelagent.chat.session.model.*;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class WorkingMemoryService {
    private static final int MAX_TURNS = 5;
    private static final int MAX_TOOL_EVIDENCE = 5;
    private static final int MAX_REFLECTIONS = 5;
    private static final int MAX_SUMMARY_LENGTH = 240;

    public WorkingMemory remember(
            SessionState currentState,
            String userMessage,
            IntentRoutingDecision route,
            ChatResponse response,
            List<ToolEvidence> toolEvidence,
            ReflectionMemory reflection
    ) {
        WorkingMemory current = currentState.memory() == null ? WorkingMemory.empty() : currentState.memory();
        TripRequirement effectiveRequirement = response.requirement() != null
                ? response.requirement()
                : currentState.requirement();

        StableProfileMemory stableProfile = mergeStableProfile(current.stableProfile(), effectiveRequirement);

        List<DecisionMemory> decisions = appendLimited(
                current.recentDecisions(),
                buildDecision(route, response, effectiveRequirement),
                MAX_TURNS
        );

        boolean destinationChanged = hasDestinationChanged(current.stableProfile(), effectiveRequirement);

        String nextItinerarySummary = response.itinerary() == null
                ? (destinationChanged ? null : current.activeItinerarySummary())
                : summarizeItinerary(response.itinerary());

        List<ToolEvidence> nextTools = destinationChanged
                ? keepLatest(filterCurrentDestination(toolEvidence, effectiveRequirement), MAX_TOOL_EVIDENCE)
                : appendAllLimited(current.recentToolEvidence(), toolEvidence, MAX_TOOL_EVIDENCE);

        return new WorkingMemory(
                stableProfile,
                nextItinerarySummary,
                appendLimited(current.recentTurns(),
                        new RecentTurn(userMessage, summarizeResponse(response), OffsetDateTime.now().toString()),
                        MAX_TURNS),
                decisions,
                nextTools,
                appendLimited(current.recentReflections(), reflection, MAX_REFLECTIONS)
        );
    }
    private StableProfileMemory mergeStableProfile(StableProfileMemory current, TripRequirement requirement) {
        if (requirement == null) {
            return current;
        }
        if (current == null) {
            return new StableProfileMemory(
                    requirement.destination(),
                    requirement.tripDays(),
                    requirement.budget(),
                    requirement.pacePreference(),
                    requirement.interests(),
                    requirement.startDate()
            );
        }

        return new StableProfileMemory(
                hasText(requirement.destination()) ? requirement.destination() : current.destination(),
                requirement.tripDays() != null ? requirement.tripDays() : current.tripDays(),
                hasText(requirement.budget()) ? requirement.budget() : current.budget(),
                hasText(requirement.pacePreference()) ? requirement.pacePreference() : current.pacePreference(),
                requirement.interests() != null && !requirement.interests().isEmpty() ? requirement.interests() : current.interests(),
                hasText(requirement.startDate()) ? requirement.startDate() : current.startDate()
        );
    }

    //决策记忆存储
    private DecisionMemory buildDecision(IntentRoutingDecision route, ChatResponse response, TripRequirement requirement) {
        return new DecisionMemory(
                route == null || route.action() == null ? null : route.action().name(),
                route == null || route.outputMode() == null ? null : route.outputMode().name(),
                requirement == null ? null : requirement.destination(),
                route == null ? null : route.topic(),
                response == null ? null : truncate(response.message(), MAX_SUMMARY_LENGTH),
                OffsetDateTime.now().toString()
        );
    }


    //目的地改变
    private boolean hasDestinationChanged(StableProfileMemory current, TripRequirement requirement) {
        if (current == null || !hasText(current.destination()) || requirement == null || !hasText(requirement.destination())) {
            return false;
        }
        return !current.destination().equalsIgnoreCase(requirement.destination());
    }

    private List<ToolEvidence> filterCurrentDestination(List<ToolEvidence> evidence, TripRequirement requirement) {
        if (evidence == null || evidence.isEmpty() || requirement == null || !hasText(requirement.destination())) {
            return List.of();
        }

        return evidence.stream()
                .filter(item -> item != null)
                .filter(item -> !hasText(item.city()) || requirement.destination().equalsIgnoreCase(item.city()))
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }



    private <T> List<T> appendLimited(List<T> current, T next, int maxSize) {
        List<T> merged = new ArrayList<>(current == null ? List.of() : current);
        if (next != null) {
            merged.add(next);
        }
        return keepLatest(merged, maxSize);
    }

    private <T> List<T> appendAllLimited(List<T> current, List<T> nextItems, int maxSize) {
        List<T> merged = new ArrayList<>(current == null ? List.of() : current);
        if (nextItems != null && !nextItems.isEmpty()) {
            merged.addAll(nextItems);
        }
        return keepLatest(merged, maxSize);
    }

    private <T> List<T> keepLatest(List<T> items, int maxSize) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, items.size() - maxSize);
        return List.copyOf(items.subList(fromIndex, items.size()));
    }

    private String summarizeResponse(ChatResponse response) {
        if (response == null) {
            return "本轮没有生成有效回复。";
        }

        String message = response.message() == null ? "" : response.message();
        String base = "类型: " + response.type() + "; 回复: " + message;
        return truncate(base, MAX_SUMMARY_LENGTH);
    }

    private String summarizeItinerary(Itinerary itinerary) {
        if (itinerary == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(itinerary.destination())
                .append(" ")
                .append(itinerary.tripDays())
                .append("天行程:");

        if (itinerary.days() != null) {
            for (ItineraryDay day : itinerary.days()) {
                builder.append(" Day").append(day.dayNumber()).append("[");

                if (day.morning() != null) {
                    builder.append("morning=").append(day.morning().activityName()).append("; ");
                }
                if (day.afternoon() != null) {
                    builder.append("afternoon=").append(day.afternoon().activityName()).append("; ");
                }
                if (day.evening() != null) {
                    builder.append("evening=").append(day.evening().activityName()).append("; ");
                }

                builder.append("]");
            }
        }

        return truncate(builder.toString(), MAX_SUMMARY_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}

