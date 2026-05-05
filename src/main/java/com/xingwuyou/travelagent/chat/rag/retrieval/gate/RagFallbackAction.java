package com.xingwuyou.travelagent.chat.rag.retrieval.gate;

public enum RagFallbackAction {
    ANSWER_WITH_RAG,
    ASK_CLARIFICATION,
    ROUTE_TO_TOOL,
    UNSUPPORTED,
    ANSWER_WITH_UNCERTAINTY
}
