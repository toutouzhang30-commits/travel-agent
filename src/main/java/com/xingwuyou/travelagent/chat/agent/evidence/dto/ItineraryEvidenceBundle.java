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
                .filter(ToolEvidence::success)
                .map(e -> {
                    if ("MapsTool".equals(e.toolName())) {
                        return "- MapsTool：dayNumber=%s，targetSlot=%s，origin=%s，destination=%s，route=%s，更新时间：%s，状态：成功"
                                .formatted(
                                        e.dayNumber(),
                                        e.targetSlot(),
                                        e.origin(),
                                        e.destination(),
                                        e.summary(),
                                        e.updatedAt()
                                );
                    }

                    return "- %s：%s，更新时间：%s，状态：成功".formatted(
                            e.toolName(),
                            e.summary(),
                            e.updatedAt()
                    );
                })
                .collect(Collectors.joining("\n"));

    }
}
