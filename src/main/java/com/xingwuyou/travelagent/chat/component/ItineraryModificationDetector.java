package com.xingwuyou.travelagent.chat.component;

import com.xingwuyou.travelagent.chat.model.Itinerary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

//修改意见，进行再次修改
//这个计划修改词太过宽泛
@Component
public class ItineraryModificationDetector {
    private static final List<String> HARD_MOD_KEYWORDS = List.of(
            "不要", "改", "换成", "太累", "太紧", "压低", "调高",
            "多安排", "少点", "重新规划", "调整", "改一下", "改成"
    );

    private static final List<String> SOFT_MOD_KEYWORDS = List.of(
            "轻松一点", "紧凑一点", "少走路", "少折返", "别太赶"
    );

    private static final List<String> QUESTION_HINTS = List.of(
            "吗", "？", "?", "适合", "怎么玩", "住哪里", "推荐", "多少钱", "价格", "天气", "会不会"
    );

    private static final Pattern DAY_PATTERN = Pattern.compile("第[一二三四五六七八九十0-9]+天");

    //判断是否修改计划表
    public boolean isModificationRequest(String message, Itinerary currentItinerary) {
        if (currentItinerary == null || !StringUtils.hasText(message)) {
            return false;
        }

        if (DAY_PATTERN.matcher(message).find()) {
            return true;
        }

        boolean hasHardModificationIntent = HARD_MOD_KEYWORDS.stream().anyMatch(message::contains);
        if (hasHardModificationIntent) {
            return true;
        }

        boolean looksLikeQuestion = QUESTION_HINTS.stream().anyMatch(message::contains);
        if (looksLikeQuestion) {
            return false;
        }

        return SOFT_MOD_KEYWORDS.stream().anyMatch(message::contains);
    }
}
