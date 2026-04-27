package com.xingwuyou.travelagent.chat.service;

import com.xingwuyou.travelagent.chat.component.*;
import com.xingwuyou.travelagent.chat.dto.*;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.rag.retrieval.*;
import com.xingwuyou.travelagent.chat.session.SessionState;
import com.xingwuyou.travelagent.chat.session.SessionStateStore;
import com.xingwuyou.travelagent.chat.tool.ToolCallDecider;
import com.xingwuyou.travelagent.chat.tool.weather.WeatherTool;
import com.xingwuyou.travelagent.chat.tool.weather.common.ToolAnswerGenerator;
import com.xingwuyou.travelagent.chat.tool.weather.common.WeatherQueryExtractor;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

//这是最重要的部分，基本上逻辑都在这里面
@Service
public class AgentOrchestratorService implements ChatService {
    private final TripRequirementExtractor extractor;
    private final RequirementCompletenessChecker checker;
    private final ItineraryGenerator generator;
    private final ItineraryModificationDetector detector;
    private final ItineraryModifier modifier;
    private final SessionStateStore sessionStateStore;
    private final RagRetrievalDecider ragRetrievalDecider;
    private final RagRetrievalService ragRetrievalService;
    private final RagContextAssembler ragContextAssembler;
    private final RagAnswerGenerator ragAnswerGenerator;
    private final ToolCallDecider toolCallDecider;
    private final WeatherQueryExtractor weatherQueryExtractor;
    private final WeatherTool weatherTool;
    private final ToolAnswerGenerator toolAnswerGenerator;

    public AgentOrchestratorService(
            TripRequirementExtractor extractor,
            RequirementCompletenessChecker checker,
            ItineraryGenerator generator,
            ItineraryModificationDetector detector,
            ItineraryModifier modifier,
            SessionStateStore sessionStateStore,
            RagRetrievalDecider ragRetrievalDecider,
            RagRetrievalService ragRetrievalService,
            RagContextAssembler ragContextAssembler,
            RagAnswerGenerator ragAnswerGenerator,
            ToolCallDecider toolCallDecider,
            WeatherQueryExtractor weatherQueryExtractor,
            WeatherTool weatherTool,
            ToolAnswerGenerator toolAnswerGenerator
    ) {
        this.extractor = extractor;
        this.checker = checker;
        this.generator = generator;
        this.detector = detector;
        this.modifier = modifier;
        this.sessionStateStore = sessionStateStore;
        this.ragRetrievalDecider = ragRetrievalDecider;
        this.ragRetrievalService = ragRetrievalService;
        this.ragContextAssembler = ragContextAssembler;
        this.ragAnswerGenerator=ragAnswerGenerator;
        this.toolCallDecider = toolCallDecider;
        this.weatherQueryExtractor = weatherQueryExtractor;
        this.weatherTool = weatherTool;
        this.toolAnswerGenerator = toolAnswerGenerator;
    }

    //为什么需要新增这个内部record?
    private record ToolFlowResult(
            ChatResponse response,
            ToolCallPayloadDto toolCallPayload,
            ToolResultPayloadDto toolResultPayload
    ) {
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        validateRequest(chatRequest);
        String sessionId = resolveSessionId(chatRequest);

        SessionState currentState = sessionStateStore.get(sessionId);

        ChatResponse response;
        if (toolCallDecider.shouldUseWeatherTool(chatRequest.message())) {
            response = executeWeatherToolFlow(sessionId, chatRequest.message(), currentState).response();
        } else {
            response = orchestrate(sessionId, chatRequest.message(), currentState);
        }

        sessionStateStore.save(sessionId, currentState.merge(response.requirement(), response.itinerary()));
        return response;
    }

    //流式回答
    //最后返回的都是agentevent，这个返回是怎么在controller用，用在前端的？
    @Override
    public Flux<AgentEvent> streamChat(ChatRequest chatRequest) {
        validateRequest(chatRequest);
        String sessionId = resolveSessionId(chatRequest);

        return Flux.defer(() -> {
            SessionState currentState = sessionStateStore.get(sessionId);
            if (toolCallDecider.shouldUseWeatherTool(chatRequest.message())) {
                ToolFlowResult flowResult = executeWeatherToolFlow(sessionId, chatRequest.message(), currentState);
                ChatResponse response = flowResult.response();

                sessionStateStore.save(sessionId, currentState.merge(response.requirement(), response.itinerary()));

                List<AgentEvent> events = new ArrayList<>();
                events.add(AgentEvent.status(sessionId, "正在调用天气工具"));
                events.add(AgentEvent.toolCall(sessionId, flowResult.toolCallPayload().summary(), flowResult.toolCallPayload()));
                events.add(AgentEvent.toolResult(sessionId, flowResult.toolResultPayload().summary(), flowResult.toolResultPayload()));
                events.add(AgentEvent.answer(sessionId, response));
                events.add(AgentEvent.done(sessionId));

                return Flux.fromIterable(events);
            }

            ChatResponse response = orchestrate(sessionId, chatRequest.message(), currentState);
            sessionStateStore.save(sessionId, currentState.merge(response.requirement(), response.itinerary()));

            AgentEvent resultEvent = switch (response.type()) {
                case FOLLOW_UP, KNOWLEDGE_ANSWER -> AgentEvent.answer(sessionId, response);
                case ITINERARY, ITINERARY_UPDATED -> AgentEvent.itinerary(sessionId, response);
            };

            List<AgentEvent> events = new ArrayList<>();
            events.add(AgentEvent.status(sessionId, "正在分析用户需求"));

            if (response.sources() != null && !response.sources().isEmpty()) {
                events.add(AgentEvent.retrieval(sessionId, "已检索到相关旅游知识", response));
            }

            events.add(buildStatusEvent(response));
            events.add(resultEvent);
            events.add(AgentEvent.done(sessionId));

            return Flux.fromIterable(events);
        });
    }

    private ToolFlowResult executeWeatherToolFlow(String sessionId, String message, SessionState currentState) {
        WeatherToolRequest request=weatherQueryExtractor.extract(message,currentState);
        ToolCallPayloadDto toolCallPayload = new ToolCallPayloadDto(
                "WeatherTool",
                buildWeatherToolCallSummary(request),
                OffsetDateTime.now().toString()
        );

        WeatherToolResponse toolResponse = weatherTool.queryWeather(request);

        ToolResultPayloadDto toolResultPayload = new ToolResultPayloadDto(
                "WeatherTool",
                toolResponse.success(),
                buildWeatherToolResultSummary(toolResponse),
                toolResponse.source(),
                toolResponse.updatedAt(),
                toolResponse.errorMessage()
        );

        String answer = toolAnswerGenerator.buildWeatherAnswer(request, toolResponse);

        ChatResponse response = new ChatResponse(
                sessionId,
                ChatResponseType.KNOWLEDGE_ANSWER,
                answer,
                currentState.requirement(),
                List.of(),
                null,
                null,
                toolResponse.source() == null ? List.of() : List.of(toolResponse.source())
        );

        return new ToolFlowResult(response, toolCallPayload, toolResultPayload);
    }

    private String buildWeatherToolCallSummary(WeatherToolRequest request) {
        String cityText = request.city() == null ? "当前目的地" : request.city();
        return "正在查询%s%s的天气".formatted(cityText, request.dayLabel());
    }

    private String buildWeatherToolResultSummary(WeatherToolResponse response) {
        if (!response.success()) {
            return response.errorMessage();
        }

        return "%s%s：白天%s，夜间%s，%s℃/%s℃".formatted(
                response.city(),
                switch (response.dayOffset()) {
                    case 1 -> "明天";
                    case 2 -> "后天";
                    default -> "今天";
                },
                response.dayWeather(),
                response.nightWeather(),
                response.dayTemp(),
                response.nightTemp()
        );
    }

    private AgentEvent buildStatusEvent(ChatResponse response) {
        return switch (response.type()) {
            case FOLLOW_UP -> AgentEvent.status(response.sessionId(), "正在补齐行程需求");
            case KNOWLEDGE_ANSWER -> AgentEvent.status(response.sessionId(),"正在整理检索到的城市知识");
            case ITINERARY -> AgentEvent.status(response.sessionId(), "正在生成行程结果");
            case ITINERARY_UPDATED -> AgentEvent.status(response.sessionId(), "正在调整现有行程");
        };
    }

    private ChatResponse orchestrate(String sessionId, String message, SessionState currentState) {
       //改成拦截价格
        if (toolCallDecider.shouldUsePricingTool(message)) {
            return buildRealtimeUnsupportedResponse(sessionId);
        }

        if (ragRetrievalDecider.shouldAnswerKnowledge(message)) {
            return handleKnowledgeAnswer(sessionId, message, currentState);
        }

        if (detector.isModificationRequest(message, currentState.itinerary())) {
            return handleItineraryModification(sessionId, message, currentState);
        }


        TripRequirement extracted = extractWithContext(message, currentState);
        TripRequirement merged = mergeRequirement(currentState.requirement(), extracted);

        List<String> missingCoreFields = checker.findMissingFields(merged);
        if (!missingCoreFields.isEmpty()) {
            return buildFollowUpResponse(sessionId, merged, missingCoreFields);
        }

        List<String> missingPreferenceFields = checker.findMissingPreferencFields(merged);
        if (!missingPreferenceFields.isEmpty()) {
            return buildFollowUpResponse(sessionId, merged, missingPreferenceFields);
        }

        List<RagRetrievalResult> retrievalResults = List.of();
        List<SourceReferenceDto> sources = List.of();
        String ragContext = "";

        //知识库对于第一次生成计划表
        if (ragRetrievalDecider.shouldRetrieveForItineraryGeneration(message, merged)) {
            RagQuery query = new RagQuery(message, merged.destination(), null, 3);
            retrievalResults = ragRetrievalService.retrieve(query);
            sources = ragContextAssembler.toSources(retrievalResults);
            ragContext = ragContextAssembler.buildContext(retrievalResults);
        }

        Itinerary itinerary = generator.generate(merged, ragContext);
        return new ChatResponse(
                sessionId,
                ChatResponseType.ITINERARY,
                "已为你生成最小行程结构。",
                merged,
                List.of(),
                null,
                itinerary,
                sources
        );
    }

    //是否回答实时问题
    //实时问题兜底
    private ChatResponse buildRealtimeUnsupportedResponse(String sessionId) {
        return new ChatResponse(
                sessionId,
                ChatResponseType.KNOWLEDGE_ANSWER,
                "这类问题属于实时信息（如天气、价格、门票），当前版本还没有接入实时工具，所以不能可靠回答。我现在可以帮助你做城市玩法建议、节奏建议和行程规划；" +
                        "等接入 WeatherTool / PricingTool 后，再支持实时天气和价格查询。",
                null,
                List.of(),
                null,
                null,
                List.of()
        );
    }

    //处理知识问答问题
    private ChatResponse handleKnowledgeAnswer(String sessionId, String message, SessionState currentState) {
        String city = extractCityFromMessage(message);

        if (!StringUtils.hasText(city)
                && currentState.requirement() != null
                && StringUtils.hasText(currentState.requirement().destination())) {
            city = currentState.requirement().destination();
        }

        List<RagRetrievalResult> results = ragRetrievalService.retrieve(new RagQuery(message, city, null, 3));
        List<SourceReferenceDto> sources = ragContextAssembler.toSources(results);
        String ragContext = ragContextAssembler.buildContext(results);
        String answer = ragAnswerGenerator.answer(message, ragContext);

        return new ChatResponse(
                sessionId,
                ChatResponseType.KNOWLEDGE_ANSWER,
                answer,
                null,
                List.of(),
                null,
                null,
                sources
        );
    }

    private String extractCityFromMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.contains("北京")) {
            return "北京";
        }
        if (message.contains("上海")) {
            return "上海";
        }
        if (message.contains("杭州")) {
            return "杭州";
        }
        return null;
    }

    private ChatResponse handleItineraryModification(String sessionId, String message, SessionState currentState) {
        TripRequirement extracted = extractWithContext(message, currentState);
        TripRequirement merged = mergeRequirement(currentState.requirement(), extracted);

        List<RagRetrievalResult> results = List.of();
        //前端
        List<SourceReferenceDto> sources = List.of();
        String modifierMessage = message;

        if (ragRetrievalDecider.shouldRetrieveForModification(message, merged)) {
            results = ragRetrievalService.retrieve(new RagQuery(message, merged.destination(), null, 3));
            //知识库的来源发给前端
            sources = ragContextAssembler.toSources(results);

            //提示词注入，知识库注入
            String ragContext = ragContextAssembler.buildContext(results);
            if (StringUtils.hasText(ragContext)) {
                modifierMessage = message + "\n\n补充参考知识：\n" + ragContext;
            }
        }

        Itinerary updatedItinerary = modifier.modify(
                modifierMessage,
                merged,
                currentState.itinerary()
        );

        return new ChatResponse(
                sessionId,
                ChatResponseType.ITINERARY_UPDATED,
                "已根据你的新要求调整行程。",
                merged,
                List.of(),
                null,
                updatedItinerary,
                sources
        );
    }

    private TripRequirement extractWithContext(String message, SessionState currentState) {
        TripRequirement currentRequirement = currentState.requirement();
        List<String> pendingFields = determinePendingFields(currentRequirement);
        return extractor.extract(message, currentRequirement, pendingFields);
    }

    private List<String> determinePendingFields(TripRequirement currentRequirement) {
        List<String> missingCoreFields = checker.findMissingFields(currentRequirement);
        if (!missingCoreFields.isEmpty()) {
            return missingCoreFields;
        }
        return checker.findMissingPreferencFields(currentRequirement);
    }

    private ChatResponse buildFollowUpResponse(String sessionId, TripRequirement merged, List<String> miss) {
        String followUp = buildFollowUpQuestion(miss);
        return new ChatResponse(
                sessionId,
                ChatResponseType.FOLLOW_UP,
                "我们需要更多信息来为您定制行程。",
                merged,
                miss,
                followUp,
                null,
                List.of()
        );
    }

    private String buildFollowUpQuestion(List<String> miss) {
        String firstMissing = miss.get(0);
        return switch (firstMissing) {
            case "destination" -> "您打算去哪里旅行？";
            case "tripDays" -> "这次旅行计划玩几天？";
            case "budget" -> "这次旅行预算大概是多少？比如 3000 元左右，或者更偏经济/均衡/品质一些。";
            case "preference" -> "为了更准确地生成行程，您更喜欢什么节奏，或者对哪些内容更感兴趣？例如轻松一点、想吃美食、喜欢人文或自然风景。";
            default -> "还有什么想告诉我的吗？";
        };
    }

    private TripRequirement mergeRequirement(TripRequirement current, TripRequirement extracted) {
        if (current == null) return extracted;
        if (extracted == null) return current;

        List<String> mergedInterests = new java.util.ArrayList<>();
        if (current.interests() != null) {
            mergedInterests.addAll(current.interests());
        }
        if (extracted.interests() != null) {
            for (String interest : extracted.interests()) {
                if (!mergedInterests.contains(interest)) {
                    mergedInterests.add(interest);
                }
            }
        }

        return new TripRequirement(
                extracted.destination() != null ? extracted.destination() : current.destination(),
                extracted.tripDays() != null ? extracted.tripDays() : current.tripDays(),
                extracted.budget() != null ? extracted.budget() : current.budget(),
                extracted.pacePreference() != null ? extracted.pacePreference() : current.pacePreference(),
                mergedInterests
        );
    }

    private String resolveSessionId(ChatRequest chatRequest) {
        return sessionStateStore.resolveSessionId(chatRequest.sessionId());
    }

    private void validateRequest(ChatRequest chatRequest) {
        if (chatRequest == null) {
            throw new IllegalArgumentException("request body must not be empty");
        }
        validateMessage(chatRequest.message());
    }

    private void validateMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
