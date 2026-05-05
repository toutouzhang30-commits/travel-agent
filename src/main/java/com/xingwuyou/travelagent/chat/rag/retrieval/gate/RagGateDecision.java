package com.xingwuyou.travelagent.chat.rag.retrieval.gate;

public record RagGateDecision(boolean allowed,
                              String reason,
                              double bestScore,
                              int matchedSourceCount,
                              double keywordHitRate,
                              boolean cityMatched,
                              RagFallbackAction recommendedFallback) {
}
