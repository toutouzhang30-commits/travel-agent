package com.xingwuyou.travelagent.chat.rag.retrieval.hybrid;

import com.xingwuyou.travelagent.chat.rag.retrieval.gate.RagGateDecision;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import com.xingwuyou.travelagent.chat.rag.retrieval.rerank.RagRerankResult;


import java.util.List;

public record HybridRagRetrievalResult(
        boolean allowed,
        RagGateDecision gateDecision,
        List<RagScoredDocument> documents,
        String context,
        long rerankTimeMs,
        boolean reranked,
        String rerankReason
) {
    public static HybridRagRetrievalResult allowed(
            RagGateDecision gateDecision,
            List<RagScoredDocument> documents,
            String context,
            RagRerankResult rerankResult
    ) {
        return new HybridRagRetrievalResult(
                true,
                gateDecision,
                documents,
                context,
                rerankResult.rerankTimeMs(),
                rerankResult.reranked(),
                rerankResult.reason()
        );
    }

    public static HybridRagRetrievalResult rejected(
            RagGateDecision gateDecision,
            List<RagScoredDocument> documents,
            RagRerankResult rerankResult
    ) {
        return new HybridRagRetrievalResult(
                false,
                gateDecision,
                documents,
                "",
                rerankResult.rerankTimeMs(),
                rerankResult.reranked(),
                rerankResult.reason()
        );
    }
}

