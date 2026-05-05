package com.xingwuyou.travelagent.chat.rag.retrieval.service;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagRetrievalFlowResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.hybrid.HybridRagRetrievalResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.hybrid.HybridRagRetrievalService;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagRecallSource;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
public class RagRetrievalFlowService {
    private final HybridRagRetrievalService hybridRagRetrievalService;

    public RagRetrievalFlowService(HybridRagRetrievalService hybridRagRetrievalService) {
        this.hybridRagRetrievalService = hybridRagRetrievalService;
    }

    public RagRetrievalFlowResult retrieve(String message, IntentRoutingDecision route, String fallbackCity, int topK) {
        if (route == null || !route.requiresRetrieval()) {
            return RagRetrievalFlowResult.notAttempted();
        }

        String city = StringUtils.hasText(route.city()) ? route.city() : fallbackCity;
        RagQuery query = new RagQuery(message, city, null, topK);

        HybridRagRetrievalResult result = hybridRagRetrievalService.retrieve(query, route);

        if (!result.allowed()) {
            String reason = result.gateDecision() == null ? "检索结果不足" : result.gateDecision().reason();
            return RagRetrievalFlowResult.rejected(reason);
        }

        return RagRetrievalFlowResult.allowed(result.context(), toRagSources(result.documents()));
    }

    private List<SourceReferenceDto> toRagSources(List<RagScoredDocument> documents) {
        if (documents == null || documents.isEmpty()) return List.of();

        return documents.stream()
                .map(document -> SourceReferenceDto.rag(
                        document.city(),
                        document.topic(),
                        document.sourceName(),
                        document.sourceUrl(),
                        document.verifiedAt(),
                        (document.recallSources() == null ? Set.<RagRecallSource>of() : document.recallSources())
                                .stream()
                                .map(Enum::name)
                                .toList(),
                        document.scoreSource(),
                        document.score(),
                        document.vectorScore(),
                        document.lexicalScore(),
                        document.rerankScore()
                ))
                .toList();
    }
}
