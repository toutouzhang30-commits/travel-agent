package com.xingwuyou.travelagent.chat.component;

import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

//需要提取
@Component
public class TripRequirementExtractor {
    private static final TripRequirement EMPTY_REQUIREMENT = new TripRequirement(null, null, null, null, List.of());

    private final ChatClient chatClient;

    //这个prompt需要优化，短答兜底规则
    public TripRequirementExtractor(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                    你是一个专业的旅游需求提取专家。
                    你的任务是从用户当前这一轮输入中提取以下字段：destination, tripDays, budget, pacePreference, interests。

                    严格遵守以下规则：
                    1. 仅提取用户在本轮明确表达的信息，严禁脑补或编造。
                    2. 你会同时看到当前已知需求和当前仍缺失的字段，它们只用于理解上下文和消歧，不能被当作本轮新提取结果直接抄回。
                    3. 如果某个字段用户本轮没有提供，请将其设置为 null。
                    4. interests 必须是一个字符串数组，如果本轮没有提到则返回空数组 []。
                    5. budget 表示用户本轮明确提供的预算信息原文，例如“3000元”“预算有限”“经济型”“5000左右”。
                    6. pacePreference 表示旅行节奏偏好，例如“轻松一点”“紧凑一些”；interests 表示兴趣偏好，例如“美食”“自然风景”“人文”。
                    7. 用户当前输入可能是对上一轮追问的极简回答，例如“300”“3天”“轻松一点”“想吃美食”。你必须结合当前仍缺失的字段顺序来理解。
                    8. 当当前仍缺失的首个字段是 budget 时，像“300”“3000”“5000左右”这类回答应优先提取为 budget，而不是忽略。
                    9. 当当前仍缺失的首个字段是 tripDays 时，像“3”“5天”“玩2天”这类回答应优先提取为 tripDays。
                    10. 当当前仍缺失的首个字段是 preference 时，像“轻松一点”“想吃美食”“喜欢自然风景”应提取到 pacePreference 或 interests。
                    11. 如果当前输入在上下文下仍然明显歧义，则不要猜测，保持为 null。
                    12. 输出必须是纯净的 JSON 格式，不要包含任何解释性文字或 Markdown 标签。
                    """)
                .build();
    }

    public TripRequirement extract(String message, TripRequirement currentRequirement, List<String> pendingFields){
        if(!StringUtils.hasText(message)){
            return EMPTY_REQUIREMENT;
        }

        TripRequirement shortReplyExtraction = extractShortReply(message.trim(), pendingFields);
        if (shortReplyExtraction != null) {
            return shortReplyExtraction;
        }

        return chatClient.prompt()
                .user(buildExtractionPrompt(message, currentRequirement, pendingFields))
                .call()
                .entity(TripRequirement.class);
    }

    private TripRequirement extractShortReply(String message, List<String> pendingFields) {
        if (pendingFields == null || pendingFields.isEmpty()) {
            return null;
        }

        String focusField = pendingFields.getFirst();
        if ("budget".equals(focusField) && message.matches("^\\d+(?:\\.\\d+)?$")) {
            return new TripRequirement(null, null, message, null, List.of());
        }

        if ("tripDays".equals(focusField) && message.matches("^\\d{1,2}$")) {
            return new TripRequirement(null, Integer.parseInt(message), null, null, List.of());
        }

        return null;
    }

    private String buildExtractionPrompt(String message, TripRequirement currentRequirement, List<String> pendingFields) {
        TripRequirement requirement = currentRequirement == null ? EMPTY_REQUIREMENT : currentRequirement;
        List<String> fields = pendingFields == null ? List.of() : pendingFields;

        return """
                当前已知需求（仅用于理解上下文，不代表本轮新增信息）：
                - destination: %s
                - tripDays: %s
                - budget: %s
                - pacePreference: %s
                - interests: %s

                当前仍缺失的字段（按优先级排序）：
                %s

                用户本轮输入：
                %s
                """.formatted(
                nullableText(requirement.destination()),
                requirement.tripDays() == null ? "null" : requirement.tripDays(),
                nullableText(requirement.budget()),
                nullableText(requirement.pacePreference()),
                requirement.interests() == null || requirement.interests().isEmpty() ? "[]" : requirement.interests(),
                fields.isEmpty() ? "[]" : fields,
                message
        );
    }

    private String nullableText(String value) {
        return StringUtils.hasText(value) ? value : "null";
    }
}
