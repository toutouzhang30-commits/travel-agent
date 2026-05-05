package com.xingwuyou.travelagent.chat.rag.retrieval.dto;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;

import java.util.List;

public record RagRetrievalFlowResult(boolean attempted,
                                     boolean allowed,
                                     String context,
                                     String rejectionReason,
                                     List<SourceReferenceDto> sources
) {
    public static RagRetrievalFlowResult notAttempted() {
        return new RagRetrievalFlowResult(false, false, "", null, List.of());
    }

    public static RagRetrievalFlowResult allowed(String context, List<SourceReferenceDto> sources) {
        return new RagRetrievalFlowResult(true, true, context == null ? "" : context, null, sources);
    }

    public static RagRetrievalFlowResult rejected(String reason) {
        return new RagRetrievalFlowResult(true, false, "", reason, List.of());
    }
}
