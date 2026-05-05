package com.xingwuyou.travelagent.chat.rag.retrieval.rerank;

import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "travel.rag.rerank",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpRagReranker implements RagReranker {

    @Override
    public RagRerankResult rerank(RagQuery query, List<RagScoredDocument> candidates) {
        long startedAt = System.nanoTime();
        long rerankTimeMs = (System.nanoTime() - startedAt) / 1_000_000;
        return RagRerankResult.fallback(candidates, rerankTimeMs, "reranker disabled");
    }
}

