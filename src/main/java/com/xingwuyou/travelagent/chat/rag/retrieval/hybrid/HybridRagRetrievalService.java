package com.xingwuyou.travelagent.chat.rag.retrieval.hybrid;

import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagRetrievalResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.gate.RagGateDecision;
import com.xingwuyou.travelagent.chat.rag.retrieval.gate.RagRetrievalGate;
import com.xingwuyou.travelagent.chat.rag.retrieval.lexical.LexicalRagRecallService;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagRecallSource;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import com.xingwuyou.travelagent.chat.rag.retrieval.rerank.RagRerankResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.rerank.RagReranker;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import com.xingwuyou.travelagent.chat.rag.retrieval.vector.PgVectorRagRecallService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HybridRagRetrievalService {

    //private final RagRetrievalService vectorRetrievalService;
    private final PgVectorRagRecallService vectorRecallService;
    private final RagRetrievalGate gate;
    private final RagReranker reranker;
    private final LexicalRagRecallService lexicalRecallService;

    public HybridRagRetrievalService(
            PgVectorRagRecallService vectorRecallService,
            LexicalRagRecallService lexicalRecallService,
            RagRetrievalGate gate,
            RagReranker reranker
    ) {
        this.vectorRecallService = vectorRecallService;
        this.lexicalRecallService = lexicalRecallService;
        this.gate = gate;
        this.reranker = reranker;
    }



    public HybridRagRetrievalResult retrieve(RagQuery query, IntentRoutingDecision route) {
        List<RagScoredDocument> vectorResults = vectorRecallService.retrieve(query).stream()
                .map(result -> toScoredDocument(query, result))
                .toList();

        List<RagScoredDocument> lexicalResults = lexicalRecallService.search(query);

        List<RagScoredDocument> candidates = mergeCandidates(vectorResults, lexicalResults);

        //精排
        RagRerankResult rerankResult = reranker.rerank(query, candidates);
        List<RagScoredDocument> rankedDocuments = rerankResult.documents();


        //网关
        RagGateDecision gateDecision = gate.decide(query, route, rankedDocuments);

        //异常拦截
        if (!gateDecision.allowed()) {
            return HybridRagRetrievalResult.rejected(
                    gateDecision,
                    rankedDocuments,
                    rerankResult
            );
        }

        //上下文大小需要小于3000，，压缩重排之后的文档
        String context = compress(rankedDocuments, 3000);
        return HybridRagRetrievalResult.allowed(
                gateDecision,
                rankedDocuments,
                context,
                rerankResult
        );
    }

    private List<RagScoredDocument> mergeCandidates(
            List<RagScoredDocument> vectorResults,
            List<RagScoredDocument> lexicalResults
    ) {
        Map<String, RagScoredDocument> merged = new LinkedHashMap<>();

        for (RagScoredDocument document : vectorResults) {
            merged.put(candidateKey(document), document);
        }

        for (RagScoredDocument lexical : lexicalResults) {
            String key = candidateKey(lexical);
            RagScoredDocument existing = merged.get(key);

            if (existing == null) {
                merged.put(key, lexical);
            } else {
                merged.put(key, mergeOne(existing, lexical));
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RagScoredDocument::score).reversed())
                .limit(20)
                .toList();
    }

    private RagScoredDocument mergeOne(RagScoredDocument existing, RagScoredDocument lexical) {
        Set<RagRecallSource> sources = new HashSet<>(existing.recallSources());
        sources.addAll(lexical.recallSources());

        double lexicalScore = Math.max(existing.lexicalScore(), lexical.lexicalScore());
        double finalScore = Math.max(existing.score(), lexicalScore);

        return new RagScoredDocument(
                existing.content(),
                existing.city(),
                existing.topic(),
                existing.sourceName(),
                existing.sourceUrl(),
                existing.verifiedAt(),
                finalScore,
                existing.vectorScore(),
                lexicalScore,
                existing.rerankScore(),
                existing.rerankRank(),
                "HYBRID_UNION",
                Math.max(existing.keywordHitRate(), lexical.keywordHitRate()),
                existing.cityMatched() || lexical.cityMatched(),
                sources
        );
    }

    private String candidateKey(RagScoredDocument document) {
        String sourceUrl = nullToEmpty(document.sourceUrl());
        if (!sourceUrl.isBlank()) {
            return sourceUrl + "|" + nullToEmpty(document.content()).hashCode();
        }

        return nullToEmpty(document.city()) + "|"
                + nullToEmpty(document.topic()) + "|"
                + nullToEmpty(document.sourceName()) + "|"
                + nullToEmpty(document.content()).hashCode();
    }



    private RagScoredDocument toScoredDocument(RagQuery query, RagRetrievalResult result) {
        boolean cityMatched = query.city() == null
                || query.city().isBlank()
                || query.city().equals(result.city());

        double keywordHitRate = 0.0;
        double vectorScore = result.score();
        double finalScore = vectorScore;

        return new RagScoredDocument(
                result.content(),
                result.city(),
                result.topic(),
                result.sourceName(),
                result.sourceUrl(),
                result.verifiedAt(),
                finalScore,
                vectorScore,
                0.0,
                0.0,
                0,
                "VECTOR",
                keywordHitRate,
                cityMatched,
                Set.of(RagRecallSource.VECTOR)
        );

    }

    //关键词命中率
    private double estimateKeywordHitRate(String question, String content) {
        if (question == null || question.isBlank() || content == null || content.isBlank()) {
            return 0.0;
        }

        int hit = 0;
        int total = 0;

        for (int i = 0; i < question.length() - 1; i++) {
            String token = question.substring(i, i + 2);
            if (token.isBlank()) {
                continue;
            }
            total++;
            if (content.contains(token)) {
                hit++;
            }
        }

        if (total == 0) {
            return 0.0;
        }

        return (double) hit / total;
    }

    //上下文压缩
    private String compress(List<RagScoredDocument> documents, int maxChars) {
        StringBuilder builder = new StringBuilder();

        for (RagScoredDocument document : documents) {
            if (builder.length() >= maxChars) {
                break;
            }

            builder.append("来源：")
                    .append(nullToEmpty(document.sourceName()))
                    .append("\n城市：")
                    .append(nullToEmpty(document.city()))
                    .append("\n主题：")
                    .append(nullToEmpty(document.topic()))
                    .append("\n内容：")
                    .append(nullToEmpty(document.content()))
                    .append("\n\n");
        }

        if (builder.length() <= maxChars) {
            return builder.toString();
        }

        return builder.substring(0, maxChars);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

