package com.xingwuyou.travelagent.chat.rag.retrieval.rerank;

import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;

import java.util.List;

public interface RagReranker {

    RagRerankResult rerank(RagQuery query, List<RagScoredDocument> candidates);
}
