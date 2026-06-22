package com.xingwuyou.travelagent.chat.session.persistence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingwuyou.travelagent.chat.dto.ChatResponse;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import com.xingwuyou.travelagent.chat.session.model.*;
import com.xingwuyou.travelagent.chat.session.persistence.entity.*;
import com.xingwuyou.travelagent.chat.session.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PersistentSessionMemoryService {
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository sessions;
    private final ConversationTurnRepository turns;
    private final TripRequirementProfileRepository profiles;
    private final ItineraryVersionRepository itineraries;

    public PersistentSessionMemoryService(
            ObjectMapper objectMapper,
            ChatSessionRepository sessions,
            ConversationTurnRepository turns,
            TripRequirementProfileRepository profiles,
            ItineraryVersionRepository itineraries
    ) {
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.turns = turns;
        this.profiles = profiles;
        this.itineraries = itineraries;
    }

    @Transactional("memoryTransactionManager")
    public void persist(
            String sessionId,
            String userMessage,
            IntentRoutingDecision route,
            ChatResponse response,
            ReflectionMemory reflectionMemory
    ) {
        upsertSession(sessionId);
        saveTurn(sessionId, userMessage, route, response);
        saveRequirement(sessionId, response.requirement());

        if (response.itinerary() != null) {
            saveItineraryVersion(sessionId, response.itinerary(), reflectionMemory);
        }
    }

    @Transactional(value = "memoryTransactionManager", readOnly = true)
    public Optional<SessionState> restore(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !sessions.existsById(sessionId)) {
            return Optional.empty();
        }

        TripRequirement requirement = profiles.findById(sessionId)
                .map(this::toRequirement)
                .orElse(null);

        Itinerary itinerary = itineraries.findTopBySessionIdOrderByVersionNoDesc(sessionId)
                .map(this::toItinerary)
                .orElse(null);

        WorkingMemory memory = buildRestoredMemory(sessionId, requirement, itinerary);
        return Optional.of(new SessionState(requirement, itinerary, memory));
    }

    private void upsertSession(String sessionId) {
        ChatSessionEntity entity = sessions.findById(sessionId).orElseGet(() -> {
            ChatSessionEntity created = new ChatSessionEntity();
            created.id = sessionId;
            created.createdAt = OffsetDateTime.now();
            created.status = "ACTIVE";
            return created;
        });

        entity.updatedAt = OffsetDateTime.now();
        entity.lastActiveAt = OffsetDateTime.now();
        sessions.save(entity);
    }

    private void saveTurn(String sessionId, String userMessage, IntentRoutingDecision route, ChatResponse response) {
        ConversationTurnEntity entity = new ConversationTurnEntity();
        entity.sessionId = sessionId;
        entity.turnIndex = turns.countBySessionId(sessionId) + 1;
        entity.userMessage = userMessage;
        entity.assistantMessage = response.message();
        entity.routeAction = route == null || route.action() == null ? null : route.action().name();
        entity.outputMode = route == null || route.outputMode() == null ? null : route.outputMode().name();
        entity.createdAt = OffsetDateTime.now();
        turns.save(entity);
    }

    private void saveRequirement(String sessionId, TripRequirement requirement) {
        if (requirement == null) {
            return;
        }

        TripRequirementProfileEntity entity = profiles.findById(sessionId).orElseGet(() -> {
            TripRequirementProfileEntity created = new TripRequirementProfileEntity();
            created.sessionId = sessionId;
            return created;
        });

        entity.destination = requirement.destination();
        entity.tripDays = requirement.tripDays();
        entity.budget = requirement.budget();
        entity.pacePreference = requirement.pacePreference();
        entity.interestsJson = writeJson(requirement.interests() == null ? List.of() : requirement.interests());
        entity.startDate = requirement.startDate();
        entity.updatedAt = OffsetDateTime.now();
        profiles.save(entity);
    }

    private void saveItineraryVersion(String sessionId, Itinerary itinerary, ReflectionMemory reflectionMemory) {
        ItineraryVersionEntity entity = new ItineraryVersionEntity();
        entity.sessionId = sessionId;
        entity.versionNo = itineraries.countBySessionId(sessionId) + 1;
        entity.destination = itinerary.destination();
        entity.tripDays = itinerary.tripDays();
        entity.itineraryJson = writeJson(itinerary);
        entity.reflectionSummary = reflectionMemory == null ? null : reflectionMemory.summary();
        entity.createdAt = OffsetDateTime.now();
        itineraries.save(entity);
    }

    private TripRequirement toRequirement(TripRequirementProfileEntity entity) {
        return new TripRequirement(
                entity.destination,
                entity.tripDays,
                entity.budget,
                entity.pacePreference,
                readStringList(entity.interestsJson),
                entity.startDate
        );
    }

    private Itinerary toItinerary(ItineraryVersionEntity entity) {
        try {
            return objectMapper.readValue(entity.itineraryJson, Itinerary.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to restore itinerary", e);
        }
    }

    private WorkingMemory buildRestoredMemory(String sessionId, TripRequirement requirement, Itinerary itinerary) {
        StableProfileMemory stableProfile = requirement == null
                ? null
                : new StableProfileMemory(
                requirement.destination(),
                requirement.tripDays(),
                requirement.budget(),
                requirement.pacePreference(),
                requirement.interests(),
                requirement.startDate()
        );

        List<RecentTurn> recentTurns = turns.findTop5BySessionIdOrderByTurnIndexDesc(sessionId)
                .stream()
                .sorted(Comparator.comparing(item -> item.turnIndex))
                .map(item -> new RecentTurn(
                        item.userMessage,
                        item.assistantMessage,
                        item.createdAt == null ? null : item.createdAt.toString()
                ))
                .toList();

        String itinerarySummary = itinerary == null
                ? null
                : itinerary.destination() + " " + itinerary.tripDays() + "天行程";

        return new WorkingMemory(
                stableProfile,
                itinerarySummary,
                recentTurns,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize memory data", e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}