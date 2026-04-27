package com.xingwuyou.travelagent.chat.rag.retrieval;

import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

//是否进行RAG检索
//区分知识问答，方案生成，方案修改
//需要升级，升级为问题类型判断
@Component
public class RagRetrievalDecider {
    //知识关键词
    private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
            "适合", "怎么玩", "住哪里", "打卡", "推荐", "攻略", "值得去吗", "怎么安排", "第一次去"
    );

    //静态知识关键词
    private static final List<String> STATIC_WEATHER_KNOWLEDGE_KEYWORDS = List.of(
            "下雨天", "雨天", "季节", "天气怎么样", "什么时候去", "夏天热不热", "冬天冷不冷"
    );

    //实时时间
    private static final List<String> REALTIME_TIME_KEYWORDS = List.of(
            "今天", "明天", "后天", "现在", "今晚", "本周", "今日", "明日"
    );

    //实时天气
    private static final List<String> REALTIME_WEATHER_KEYWORDS = List.of(
            "天气", "气温", "温度", "下雨", "有雨", "晴", "阴"
    );

    //价格
    private static final List<String> REALTIME_PRICE_KEYWORDS = List.of(
            "门票", "票价", "价格", "多少钱", "余票", "还有票"
    );

    public boolean shouldAnswerKnowledge(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }

        return containsAny(message, KNOWLEDGE_KEYWORDS)
                || containsAny(message, STATIC_WEATHER_KNOWLEDGE_KEYWORDS)
                || (message.contains("天气") && !containsAny(message, REALTIME_TIME_KEYWORDS));
    }

    public boolean isRealtimeQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }

        boolean hasRealtimeTime = containsAny(message, REALTIME_TIME_KEYWORDS);
        boolean asksRealtimeWeather = hasRealtimeTime && containsAny(message, REALTIME_WEATHER_KEYWORDS);
        boolean asksRealtimePrice = containsAny(message, REALTIME_PRICE_KEYWORDS);

        return asksRealtimeWeather || asksRealtimePrice;
    }

    public boolean shouldRetrieveForItineraryGeneration(String message, TripRequirement requirement) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return shouldAnswerKnowledge(message)
                || (requirement != null && StringUtils.hasText(requirement.destination()));
    }

    public boolean shouldRetrieveForModification(String message, TripRequirement requirement) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return shouldAnswerKnowledge(message)
                || message.contains("太累")
                || message.contains("调整")
                || message.contains("重新规划")
                || message.contains("少走路")
                || message.contains("别太赶");
    }

    private boolean containsAny(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }

}
