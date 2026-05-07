package com.xingwuyou.travelagent.chat.tool.weather.common;

import com.xingwuyou.travelagent.chat.session.model.SessionState;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherQueryParameters;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

//天气参数提取器
@Component
public class WeatherQueryExtractor {
    private static final int DEFAULT_DAY_OFFSET = 0;
    private static final int DEFAULT_DAYS = 1;
    private static final int MAX_DAYS = 7;

    private final ChatClient chatClient;

    public WeatherQueryExtractor(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        你是天气查询参数抽取器。
                        你的任务是把用户自然语言转换成结构化天气查询参数。

                        只输出纯 JSON，不要 Markdown，不要解释。

                        字段说明：
                        - city: 城市名，例如 北京、上海、杭州。如果用户没有明确说城市，但上下文有目的地，可以使用上下文目的地。
                        - dayOffset: 从今天开始的日期偏移。今天=0，明天=1，后天=2。
                        - days: 连续查询天数。单日查询为 1。
                        - timeExpression: 用户原文中的时间表达，例如 今天、明天、明后天、未来三天、近一周、周末。
                        - reason: 简短说明你为什么这样抽取。

                        规则：
                        1. “杭州今天的天气” -> dayOffset=0, days=1
                        2. “杭州明天会下雨吗” -> dayOffset=1, days=1
                        3. “杭州后天温度” -> dayOffset=2, days=1
                        4. “杭州未来三天天气” -> dayOffset=0, days=3
                        5. “杭州近一周天气” -> dayOffset=0, days=7
                        6. “杭州明后天天气” -> dayOffset=1, days=2
                        7. “杭州周末天气” 如果无法确定具体日期，保持 dayOffset=0, days=2，并在 reason 中说明不确定。
                        8. 如果用户没有说天数，days=1。
                        9. days 不要超过 7。
                        10. 如果城市无法从用户输入或上下文判断，city=null。
                        """)
                .build();
    }

    public WeatherToolRequest extract(String message, SessionState sessionState) {
        WeatherQueryParameters parameters = extractByLlm(message, sessionState);
        //需要先从问题中获取参数
        String city = parameters ==null ? null: parameters.city();

        if (!StringUtils.hasText(city)
                && sessionState != null
                && sessionState.requirement() != null
                && StringUtils.hasText(sessionState.requirement().destination())) {
            city = sessionState.requirement().destination();
        }


        int dayOffset = normalizeDayOffset(parameters == null ? null : parameters.dayOffset());
        int days = normalizeDays(parameters == null ? null : parameters.days());


        return new WeatherToolRequest(
                city,
                dayOffset,
                days,
                parameters == null ? null : parameters.timeExpression(),
                message
        );

    }

    private WeatherQueryParameters extractByLlm(String message, SessionState sessionState) {
        try {
            return chatClient.prompt()
                    .user(buildPrompt(message, sessionState))
                    .call()
                    .entity(WeatherQueryParameters.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildPrompt(String message, SessionState sessionState) {
        String destination = null;
        if (sessionState != null
                && sessionState.requirement() != null
                && StringUtils.hasText(sessionState.requirement().destination())) {
            destination = sessionState.requirement().destination();
        }

        return """
                当前上下文：
                - destination: %s

                用户问题：
                %s
                """.formatted(
                destination == null ? "null" : destination,
                message
        );
    }

    private int normalizeDayOffset(Integer dayOffset) {
        if (dayOffset == null || dayOffset < 0) {
            return DEFAULT_DAY_OFFSET;
        }
        return dayOffset;
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }


}