package com.xingwuyou.travelagent.chat.rag.eval.dto;

import java.util.List;

public record RagEvalCase(
        String id,
        RagEvalCaseType caseType,
        String question,
        String city,
        RagExpectedAction expectedAction,
        List<String> expectedTopics,
        List<String> expectedKeywords,
        List<String> expectedSourceNames
) {
    public RagEvalCase {
        expectedTopics = expectedTopics == null ? List.of() : List.copyOf(expectedTopics);
        expectedKeywords = expectedKeywords == null ? List.of() : List.copyOf(expectedKeywords);
        expectedSourceNames = expectedSourceNames == null ? List.of() : List.copyOf(expectedSourceNames);
    }

    public boolean positive() {
        return caseType == RagEvalCaseType.POSITIVE;
    }

    public boolean negative() {
        return caseType == RagEvalCaseType.NEGATIVE;
    }

    public boolean shouldRetrieve() {
        return expectedAction == RagExpectedAction.RETRIEVE;
    }

    public boolean shouldRejectRag() {
        return expectedAction == RagExpectedAction.NO_RETRIEVAL
                || expectedAction == RagExpectedAction.TOOL_REQUIRED
                || expectedAction == RagExpectedAction.UNSUPPORTED;
    }
}

