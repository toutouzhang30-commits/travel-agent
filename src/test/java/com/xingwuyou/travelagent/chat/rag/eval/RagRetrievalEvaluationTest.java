package com.xingwuyou.travelagent.chat.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingwuyou.travelagent.chat.rag.eval.dto.RagEvalCase;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.hybrid.HybridRagRetrievalResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.hybrid.HybridRagRetrievalService;
import com.xingwuyou.travelagent.chat.routing.IntentAction;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SpringBootTest
@ActiveProfiles("test")
class RagRetrievalEvaluationTest {

    @Autowired
    private HybridRagRetrievalService hybridRagRetrievalService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateRagRetrieval() throws Exception {
        var resource = Objects.requireNonNull(
                getClass().getResourceAsStream("/rag/eval/travel-rag-eval.jsonl"),
                "找不到 /rag/eval/travel-rag-eval.jsonl，请确认文件在 src/test/resources/rag/eval/ 下"
        );

        int total = 0;
        int shouldRetrieveTotal = 0;
        int shouldRejectTotal = 0;

        int acceptedWhenShouldRetrieve = 0;
        int rejectedWhenShouldReject = 0;
        int falseAccept = 0;
        int falseReject = 0;

        List<Long> rerankTimes = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                RagEvalCase evalCase = objectMapper.readValue(line, RagEvalCase.class);
                IntentRoutingDecision route = buildRoute(evalCase);

                RagQuery query = new RagQuery(
                        evalCase.question(),
                        evalCase.city(),
                        null,
                        10
                );

                HybridRagRetrievalResult result = hybridRagRetrievalService.retrieve(query, route);
                rerankTimes.add(result.rerankTimeMs());

                total++;

                if (evalCase.shouldRetrieve()) {
                    shouldRetrieveTotal++;

                    if (result.allowed()) {
                        acceptedWhenShouldRetrieve++;
                    } else {
                        falseReject++;
                    }
                }

                if (evalCase.shouldRejectRag()) {
                    shouldRejectTotal++;

                    if (!result.allowed()) {
                        rejectedWhenShouldReject++;
                    } else {
                        falseAccept++;
                    }
                }

                System.out.printf(
                        "[%s] expectedAction=%s routeAction=%s allowed=%s reason=%s bestScore=%.3f keywordHitRate=%.3f sources=%d rerankTimeMs=%d reranked=%s rerankReason=%s%n",
                        evalCase.id(),
                        evalCase.expectedAction(),
                        route.action(),
                        result.allowed(),
                        result.gateDecision().reason(),
                        result.gateDecision().bestScore(),
                        result.gateDecision().keywordHitRate(),
                        result.documents().size(),
                        result.rerankTimeMs(),
                        result.reranked(),
                        result.rerankReason()
                );


                result.documents().stream().findFirst().ifPresent(top -> System.out.printf(
                        "    top1 city=%s topic=%s score=%.3f vectorScore=%.3f rerankScore=%.3f rerankRank=%d scoreSource=%s source=%s%n",
                        top.city(),
                        top.topic(),
                        top.score(),
                        top.vectorScore(),
                        top.rerankScore(),
                        top.rerankRank(),
                        top.scoreSource(),
                        top.sourceName()
                ));
            }
        }

        double recall = shouldRetrieveTotal == 0
                ? 0
                : (double) acceptedWhenShouldRetrieve / shouldRetrieveTotal;

        double rejectRecall = shouldRejectTotal == 0
                ? 0
                : (double) rejectedWhenShouldReject / shouldRejectTotal;

        double falseAcceptRate = shouldRejectTotal == 0
                ? 0
                : (double) falseAccept / shouldRejectTotal;

        double falseRejectRate = shouldRetrieveTotal == 0
                ? 0
                : (double) falseReject / shouldRetrieveTotal;

        double avgRerankTimeMs = rerankTimes.isEmpty()
                ? 0
                : rerankTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        long p95RerankTimeMs = percentile(rerankTimes, 0.95);

        System.out.println();
        System.out.println("===== RAG Evaluation Report =====");
        System.out.println("total=" + total);
        System.out.println("shouldRetrieveTotal=" + shouldRetrieveTotal);
        System.out.println("shouldRejectTotal=" + shouldRejectTotal);
        System.out.printf("Recall=%.3f%n", recall);
        System.out.printf("RejectRecall=%.3f%n", rejectRecall);
        System.out.printf("FalseAcceptRate=%.3f%n", falseAcceptRate);
        System.out.printf("FalseRejectRate=%.3f%n", falseRejectRate);
        System.out.printf("AvgRerankTimeMs=%.1f%n", avgRerankTimeMs);
        System.out.println("P95RerankTimeMs=" + p95RerankTimeMs);
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }

        List<Long> sorted = values.stream()
                .sorted()
                .toList();

        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));

        return sorted.get(index);
    }



    private IntentRoutingDecision buildRoute(RagEvalCase evalCase) {
        IntentAction action = switch (evalCase.expectedAction()) {
            case RETRIEVE, NO_RETRIEVAL -> IntentAction.KNOWLEDGE_QA;
            case TOOL_REQUIRED -> IntentAction.WEATHER_TOOL;
            case UNSUPPORTED -> IntentAction.ROUTING_FAILED;
        };

        return new IntentRoutingDecision(
                action,
                action == IntentAction.KNOWLEDGE_QA,
                null,
                evalCase.city(),
                firstTopic(evalCase),
                1.0,
                "RAG evaluation case"
        );
    }


    private String firstTopic(RagEvalCase evalCase) {
        if (evalCase.expectedTopics().isEmpty()) {
            return null;
        }
        return evalCase.expectedTopics().get(0);
    }
}
