package com.xingwuyou.travelagent.chat.session.model;

import java.util.List;

public record WorkingMemory(StableProfileMemory stableProfile,
                            String activeItinerarySummary,
                            List<RecentTurn> recentTurns,
                            List<DecisionMemory> recentDecisions,
                            List<ToolEvidence> recentToolEvidence,
                            List<ReflectionMemory> recentReflections
) {
    public static WorkingMemory empty() {
        return new WorkingMemory(null, null, List.of(), List.of(), List.of(), List.of());
    }
}
