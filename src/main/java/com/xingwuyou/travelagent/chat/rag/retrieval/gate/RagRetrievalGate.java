package com.xingwuyou.travelagent.chat.rag.retrieval.gate;

import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;
import com.xingwuyou.travelagent.chat.routing.IntentAction;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagRetrievalGate {
    private static final double MIN_BEST_SCORE = 0.45;
    private static final int MIN_SOURCE_COUNT = 1;
    private static final double MIN_KEYWORD_HIT_RATE = 0.20;
    private static final double MIN_VECTOR_SCORE = 0.45;
    private static final double MIN_DASHSCOPE_RERANK_SCORE = 0.15;


    public RagGateDecision decide(
            RagQuery query,
            IntentRoutingDecision route,
            //检索出来的文档
            List<RagScoredDocument> documents
    ) {
        //意图拦截
        if (route.action() != IntentAction.KNOWLEDGE_QA
                && route.action() != IntentAction.GENERATE_ITINERARY
                && route.action() != IntentAction.MODIFY_ITINERARY) {
            return rejected(
                    "当前路由动作不允许使用 RAG",
                    0,
                    0,
                    0,
                    false,
                    route.action() == IntentAction.WEATHER_TOOL
                            || route.action() == IntentAction.PRICING_TOOL
                            || route.action() == IntentAction.MAPS_TOOL
                            ? RagFallbackAction.ROUTE_TO_TOOL
                            : RagFallbackAction.UNSUPPORTED
            );
        }


        if (documents == null || documents.isEmpty()) {
            return rejected("知识库没有召回可用结果", 0, 0, 0, false, RagFallbackAction.ASK_CLARIFICATION);
        }

        RagScoredDocument best = documents.get(0);
        double bestScore = best.score();
        //需要确定证据的来源
        int sourceCount = (int) documents.stream()
                .map(RagScoredDocument::sourceName)
                .filter(source -> source != null && !source.isBlank())
                .distinct()
                .count();

        //验证地理位置和关键词的准确性
        boolean cityMatched = documents.stream().anyMatch(RagScoredDocument::cityMatched);
        double keywordHitRate = best.keywordHitRate();

        //硬性得分阈值
        double minScore = minScoreFor(best);

        if (bestScore < minScore) {
            return rejected(
                    "最高相关度低于 " + best.scoreSource() + " 底线阈值",
                    bestScore,
                    sourceCount,
                    keywordHitRate,
                    cityMatched,
                    RagFallbackAction.ANSWER_WITH_UNCERTAINTY
            );
        }


        if (sourceCount < MIN_SOURCE_COUNT) {
            return rejected("可用来源数量不足", bestScore, sourceCount, keywordHitRate, cityMatched,
                    RagFallbackAction.ANSWER_WITH_UNCERTAINTY);
        }

        if (!cityMatched) {
            return rejected("召回内容与目标城市不匹配", bestScore, sourceCount, keywordHitRate, false,
                    RagFallbackAction.ASK_CLARIFICATION);
        }

       /* if (keywordHitRate < MIN_KEYWORD_HIT_RATE) {
            return rejected("词法命中率过低", bestScore, sourceCount, keywordHitRate, cityMatched,
                    RagFallbackAction.ANSWER_WITH_UNCERTAINTY);
        }*/


        return new RagGateDecision(true, "RAG 检索结果通过底线阈值", bestScore, sourceCount,
                keywordHitRate, cityMatched, RagFallbackAction.ANSWER_WITH_RAG);
    }

    private double minScoreFor(RagScoredDocument document) {
        if ("DASHSCOPE_RERANK".equals(document.scoreSource())) {
            return MIN_DASHSCOPE_RERANK_SCORE;
        }

        return MIN_VECTOR_SCORE;
    }


    private RagGateDecision rejected(
            String reason,
            double bestScore,
            int sourceCount,
            double keywordHitRate,
            boolean cityMatched,
            RagFallbackAction fallback
    ) {
        return new RagGateDecision(false, reason, bestScore, sourceCount, keywordHitRate, cityMatched, fallback);
    }
}
