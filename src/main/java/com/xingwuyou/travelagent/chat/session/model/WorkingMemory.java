package com.xingwuyou.travelagent.chat.session.model;

import java.util.List;

public record WorkingMemory(String activeItinerarySummary,
                            List<RecentTurn> recentTurns,
                            List<ToolEvidence> recentToolEvidence,
                            List<ReflectionMemory> recentReflections
) {
    public static WorkingMemory empty() {
        return new WorkingMemory(null, List.of(), List.of(), List.of());
    }
}
