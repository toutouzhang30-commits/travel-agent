package com.xingwuyou.travelagent.chat.routing;

import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.session.model.SessionState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntentRoutingService {
    private final ChatClient chatClient;
    public IntentRoutingService(ChatClient.Builder builder){
        this.chatClient=builder
                .defaultSystem("""
                        你是旅游 Agent 的意图路由器。
                        你的任务是根据用户本轮输入和当前会话状态，选择唯一的下一步 action。

                        只能选择以下 action：
                        - COLLECT_REQUIREMENT：用户还在补充目的地、天数、预算、偏好等需求
                        - GENERATE_ITINERARY：需求已经足够，应生成初版行程
                        - MODIFY_ITINERARY：用户在修改已有 itinerary
                        - KNOWLEDGE_QA：用户在问静态/半静态旅游知识、玩法、住宿区域、攻略建议
                        - WEATHER_TOOL：用户在问实时天气、明天/今天/后天是否下雨、温度等
                        - MAPS_TOOL：用户在问路线、距离、交通耗时
                        - PRICING_TOOL：用户在问实时票价、门票价格、余票等
                        - ROUTING_FAILED：无法判断

                        严格规则：
                        1. 你必须输出纯 JSON，不要 Markdown，不要解释。
                        2. 不要使用关键词表思维，要根据语义判断。
                        3. “杭州雨天怎么玩”属于 KNOWLEDGE_QA，不是 WEATHER_TOOL。
                        4. “杭州明天会下雨吗”属于 WEATHER_TOOL。
                        5. “上海迪士尼门票多少钱”属于 PRICING_TOOL。
                        6. “第二天太累了，轻松一点”在已有 itinerary 时属于 MODIFY_ITINERARY。
                        7. “杭州 3 天 3000 轻松一点”通常属于 GENERATE_ITINERARY。
                        8. 如果信息不足以生成 itinerary，选择 COLLECT_REQUIREMENT。
                        9. requiresRetrieval 只在 KNOWLEDGE_QA、GENERATE_ITINERARY、MODIFY_ITINERARY 需要知识补充时为 true。
                        10. toolName 只在 WEATHER_TOOL / MAPS_TOOL / PRICING_TOOL 时填写。
                        11. 用户询问“第一次去怎么玩更顺”“住哪片区更方便”“胡同和美食怎么串起来”“拍照打卡适合哪些区域”这类玩法思路、区域顺序、路线串联、攻略建议时，优先选择 KNOWLEDGE_QA。
                        12. 即使用户说了“两天”“周末”“怎么安排”，只要没有明确要求生成完整行程表、计划表、按天/按时间段 itinerary，就不要直接选择 GENERATE_ITINERARY。
                        13. 只有当用户明确要求“生成/做一份/给我排一个”完整 itinerary，或目的地、天数、预算、节奏、兴趣等约束已经足够且语义目标是拿到可执行行程表时，才选择 GENERATE_ITINERARY。
                        14. 示例：“北京第一次去怎么安排更顺？”属于 KNOWLEDGE_QA；“上海周末两天偏拍照打卡怎么安排？”属于 KNOWLEDGE_QA；“北京胡同和本地美食怎么串起来玩？”属于 KNOWLEDGE_QA。
                        """)
                .build();
    }

    public IntentRoutingDecision route(String message, SessionState state) {
        if (!StringUtils.hasText(message)) {
            return IntentRoutingDecision.failed("message is blank");
        }

        try {
            //强制结构画输出
            return chatClient.prompt()
                    .user(buildPrompt(message, state))
                    .call()
                    .entity(IntentRoutingDecision.class);
        } catch (Exception e) {
            return IntentRoutingDecision.failed("routing model failed: " + e.getMessage());
        }
    }

    //注入工作记忆
    private String buildPrompt(String message, SessionState state) {
    TripRequirement requirement = state == null ? null : state.requirement();
    Itinerary itinerary = state == null ? null : state.itinerary();

        return """
                当前会话状态：
                - destination: %s
                - tripDays: %s
                - budget: %s
                - pacePreference: %s
                - interests: %s
                - hasItinerary: %s

                用户本轮输入：
                %s
                """.formatted(
            requirement == null ? "null" : requirement.destination(),
    requirement == null ? "null" : requirement.tripDays(),
    requirement == null ? "null" : requirement.budget(),
    requirement == null ? "null" : requirement.pacePreference(),
    requirement == null || requirement.interests() == null ? "[]" : requirement.interests(),
    itinerary != null,
    message
        );
}
}
