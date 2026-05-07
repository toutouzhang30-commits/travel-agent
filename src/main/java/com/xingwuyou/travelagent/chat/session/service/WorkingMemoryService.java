package com.xingwuyou.travelagent.chat.session.service;

import com.xingwuyou.travelagent.chat.dto.ChatResponse;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.model.ItineraryDay;
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
            ChatResponse response,
            List<ToolEvidence> toolEvidence,
            ReflectionMemory reflection
    ) {
        WorkingMemory current = currentState.memory() == null
                ? WorkingMemory.empty()
                : currentState.memory();

        List<RecentTurn> turns = appendLimited(
                current.recentTurns(),
                new RecentTurn(userMessage, summarizeResponse(response), OffsetDateTime.now().toString()),
                MAX_TURNS
        );

        List<ToolEvidence> tools = appendAllLimited(
                current.recentToolEvidence(),
                toolEvidence == null ? List.of() : toolEvidence,
                MAX_TOOL_EVIDENCE
        );

        List<ReflectionMemory> reflections = reflection == null
                ? current.recentReflections()
                : appendLimited(current.recentReflections(), reflection, MAX_REFLECTIONS);

        String itinerarySummary = response.itinerary() == null
                ? current.activeItinerarySummary()
                : summarizeItinerary(response.itinerary());

        return new WorkingMemory(
                itinerarySummary,
                turns,
                tools,
                reflections
        );
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
        String base = "类型：" + response.type() + "；回复：" + message;

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
                .append("天行程：");

        if (itinerary.days() != null) {
            for (ItineraryDay day : itinerary.days()) {
                builder.append(" Day")
                        .append(day.dayNumber())
                        .append("[");

                if (day.morning() != null) {
                    builder.append("上午：").append(day.morning().activityName()).append("；");
                }
                if (day.afternoon() != null) {
                    builder.append("下午：").append(day.afternoon().activityName()).append("；");
                }
                if (day.evening() != null) {
                    builder.append("晚上：").append(day.evening().activityName()).append("；");
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

