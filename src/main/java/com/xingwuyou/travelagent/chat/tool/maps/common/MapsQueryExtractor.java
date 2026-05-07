package com.xingwuyou.travelagent.chat.tool.maps.common;

import com.xingwuyou.travelagent.chat.session.model.SessionState;
import com.xingwuyou.travelagent.chat.tool.maps.dto.MapsRouteRequest;
import com.xingwuyou.travelagent.chat.tool.maps.dto.TravelMode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Component
public class MapsQueryExtractor {
    private final ChatClient chatClient;

    public MapsQueryExtractor(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        你是旅游地图路线查询参数抽取器。
                        你的任务是把用户自然语言转成 MapsRouteRequest。

                        只输出 JSON，不要 Markdown，不要解释。

                        字段说明：
                        - city: 城市名，例如 北京、上海、杭州。用户没说城市但上下文有目的地时，使用上下文目的地。
                        - origin: 起点，例如酒店、景点、车站、商圈。
                        - destination: 终点，例如酒店、景点、车站、商圈。
                        - travelMode: WALKING、TRANSIT、DRIVING、CYCLING。用户未指定时使用 TRANSIT。
                        - timeExpression: 用户原始时间表达。
                        - rawQuestion: 用户原始问题。

                        规则：
                        1. “从西湖到灵隐寺坐公交多久” -> TRANSIT。
                        2. “故宫到南锣鼓巷多远” -> TRANSIT。
                        3. “打车从北京南站到故宫多久” -> DRIVING。
                        4. “步行到外滩要多久” -> WALKING。
                        5. 不要用关键词表解释，只做语义抽取。
                        """)
                .build();
    }

    public MapsRouteRequest extract(String message, SessionState state) {
        MapsRouteRequest extracted = extractByLlm(message, state);

        String fallbackCity = state != null
                && state.requirement() != null
                && StringUtils.hasText(state.requirement().destination())
                ? state.requirement().destination()
                : null;

        return new MapsRouteRequest(
                hasText(extracted == null ? null : extracted.city()) ? extracted.city() : fallbackCity,
                extracted == null ? null : extracted.origin(),
                extracted == null ? null : extracted.destination(),
                extracted == null || extracted.travelMode() == null ? TravelMode.TRANSIT : extracted.travelMode(),
                extracted == null ? null : extracted.timeExpression(),
                message
        );
    }

    private MapsRouteRequest extractByLlm(String message, SessionState state) {
        try {
            return chatClient.prompt()
                    .user(buildPrompt(message, state))
                    .call()
                    .entity(MapsRouteRequest.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildPrompt(String message, SessionState state) {
        String destination = state != null
                && state.requirement() != null
                ? state.requirement().destination()
                : null;

        return """
                当前系统日期：%s
                当前会话目的地：%s

                用户问题：
                %s
                """.formatted(
                LocalDate.now(),
                destination == null ? "null" : destination,
                message
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
