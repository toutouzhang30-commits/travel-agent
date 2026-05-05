package com.xingwuyou.travelagent.chat.rag.retrieval.rerank;

import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;

import java.util.List;

public record RagRerankResult(
        List<RagScoredDocument> documents,
        long rerankTimeMs,
        boolean reranked,
        String reason
) {
    public static RagRerankResult fallback(List<RagScoredDocument> documents, long rerankTimeMs, String reason) {
        return new RagRerankResult(documents, rerankTimeMs, false, reason);
    }

    public static RagRerankResult reranked(List<RagScoredDocument> documents, long rerankTimeMs) {
        return new RagRerankResult(documents, rerankTimeMs, true, "rerank completed");
    }
}