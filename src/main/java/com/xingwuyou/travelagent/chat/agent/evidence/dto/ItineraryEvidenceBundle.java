package com.xingwuyou.travelagent.chat.agent.evidence.dto;

import com.xingwuyou.travelagent.chat.session.model.ToolEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

//统一承载地图工具
public record ItineraryEvidenceBundle(List<ToolEvidence> toolEvidence) {
    public static ItineraryEvidenceBundle empty() {
        return new ItineraryEvidenceBundle(List.of());
    }

    public ItineraryEvidenceBundle merge(ItineraryEvidenceBundle other) {
        List<ToolEvidence> merged = new ArrayList<>(toolEvidence == null ? List.of() : toolEvidence);
        if (other != null && other.toolEvidence() != null) {
            merged.addAll(other.toolEvidence());
        }
        return new ItineraryEvidenceBundle(merged);
    }

    public String toPromptContext() {
        if (toolEvidence == null || toolEvidence.isEmpty()) {
            return "";
        }
        return toolEvidence.stream()
                .map(e -> "- %s：%s，更新时间：%s，状态：%s".formatted(
                        e.toolName(),
                        e.summary(),
                        e.updatedAt(),
                        e.success() ? "成功" : "失败：" + e.errorMessage()
                ))
                .collect(Collectors.joining("\n"));
    }
}
