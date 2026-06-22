package com.xingwuyou.travelagent.chat.session.context;

import com.xingwuyou.travelagent.chat.session.model.WorkingMemory;
import org.springframework.stereotype.Component;

//上下文组装器件
@Component
public class WorkingMemoryContextAssembler {
    public String build(WorkingMemory memory) {
        if (memory == null) return "";

        StringBuilder sb = new StringBuilder();
        if (memory.stableProfile() != null) sb.append("stableProfile=").append(memory.stableProfile()).append("\n");
        if (memory.activeItinerarySummary() != null) sb.append("activeItinerarySummary=").append(memory.activeItinerarySummary()).append("\n");
        if (memory.recentTurns() != null) sb.append("recentTurns=").append(memory.recentTurns()).append("\n");
        if (memory.recentDecisions() != null) sb.append("recentDecisions=").append(memory.recentDecisions()).append("\n");
        if (memory.recentToolEvidence() != null) sb.append("recentToolEvidence=").append(memory.recentToolEvidence()).append("\n");
        if (memory.recentReflections() != null) sb.append("recentReflections=").append(memory.recentReflections()).append("\n");
        return sb.toString();
    }
}
