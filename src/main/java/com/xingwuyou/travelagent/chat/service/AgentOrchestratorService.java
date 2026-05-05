package com.xingwuyou.travelagent.chat.service;

import com.xingwuyou.travelagent.chat.component.*;
import com.xingwuyou.travelagent.chat.dto.*;
import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.rag.retrieval.*;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagQuery;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagRetrievalFlowResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.dto.RagRetrievalResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.service.RagRetrievalFlowService;
import com.xingwuyou.travelagent.chat.session.SessionState;
import com.xingwuyou.travelagent.chat.session.SessionStateStore;
import com.xingwuyou.travelagent.chat.tool.weather.WeatherTool;
import com.xingwuyou.travelagent.chat.tool.weather.common.ToolAnswerGenerator;
import com.xingwuyou.travelagent.chat.tool.weather.common.WeatherQueryExtractor;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import com.xingwuyou.travelagent.chat.routing.IntentAction;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingDecision;
import com.xingwuyou.travelagent.chat.routing.IntentRoutingService;
import com.xingwuyou.travelagent.chat.rag.retrieval.hybrid.HybridRagRetrievalResult;
import com.xingwuyou.travelagent.chat.rag.retrieval.hybrid.HybridRagRetrievalService;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagRecallSource;
import com.xingwuyou.travelagent.chat.rag.retrieval.model.RagScoredDocument;



import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

//这是最重要的部分，基本上逻辑都在这里面
@Service
public class AgentOrchestratorService implements ChatService {
    private final TripRequirementExtractor extractor;
    private final RequirementCompletenessChecker checker;
    private final ItineraryGenerator generator;
    //private final ItineraryModificationDetector detector;
    private final ItineraryModifier modifier;
    private final SessionStateStore sessionStateStore;
    //private final RagRetrievalService ragRetrievalService;
    //private final RagContextAssembler ragContextAssembler;
    private final RagAnswerGenerator ragAnswerGenerator;
    //private final ToolCallDecider toolCallDecider;
    private final WeatherQueryExtractor weatherQueryExtractor;
    private final WeatherTool weatherTool;
    private final ToolAnswerGenerator toolAnswerGenerator;
    private final IntentRoutingService intentRoutingService;
    //private final HybridRagRetrievalService hybridRagRetrievalService;
    private final RagRetrievalFlowService ragRetrievalFlowService;



    public AgentOrchestratorService(
            TripRequirementExtractor extractor,
            RequirementCompletenessChecker checker,
            ItineraryGenerator generator,
            //ItineraryModificationDetector detector,
            ItineraryModifier modifier,
            SessionStateStore sessionStateStore,
            //RagRetrievalService ragRetrievalService,
            //RagContextAssembler ragContextAssembler,
            RagAnswerGenerator ragAnswerGenerator,
            //ToolCallDecider toolCallDecider,
            WeatherQueryExtractor weatherQueryExtractor,
            WeatherTool weatherTool,
            ToolAnswerGenerator toolAnswerGenerator,
            IntentRoutingService intentRoutingService,
            //HybridRagRetrievalService hybridRagRetrievalService
            RagRetrievalFlowService ragRetrievalFlowService
    ) {
        this.extractor = extractor;
        this.checker = checker;
        this.generator = generator;
        //this.detector = detector;
        this.modifier = modifier;
        this.sessionStateStore = sessionStateStore;
        //this.ragRetrievalService = ragRetrievalService;
        //this.ragContextAssembler = ragContextAssembler;
        this.ragAnswerGenerator=ragAnswerGenerator;
        //this.toolCallDecider = toolCallDecider;
        this.weatherQueryExtractor = weatherQueryExtractor;
        this.weatherTool = weatherTool;
        this.toolAnswerGenerator = toolAnswerGenerator;
        this.intentRoutingService = intentRoutingService;
        //this.hybridRagRetrievalService = hybridRagRetrievalService;
        this.ragRetrievalFlowService = ragRetrievalFlowService;
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
        //这个确定问题的类型
        IntentRoutingDecision route = intentRoutingService.route(chatRequest.message(), currentState);

        ChatResponse response = orchestrateByRoute(sessionId, chatRequest.message(), currentState, route);

        sessionStateStore.save(sessionId, currentState.merge(response.requirement(), response.itinerary()));
        return response;
    }

    //目前走什么路径使用这个方法进行判断
    private ChatResponse orchestrateByRoute(String sessionId, String message, SessionState currentState, IntentRoutingDecision route) {
        if (route == null || route.action() == null || route.outputMode() == null) {
            return buildRoutingFailedResponse(sessionId);
        }

        return switch (route.outputMode()) {
            case TOOL_RESULT -> switch (route.action()) {
                case WEATHER_TOOL -> executeWeatherToolFlow(sessionId, message, currentState).response();
                case PRICING_TOOL, MAPS_TOOL -> buildRealtimeUnsupportedResponse(sessionId);
                default -> buildRoutingFailedResponse(sessionId);
            };
            case KNOWLEDGE_ANSWER -> handleKnowledgeAnswer(sessionId, message, currentState, route);
            case ITINERARY -> handleRequirementAndGeneration(sessionId, message, currentState, route);
            case ITINERARY_UPDATE -> handleItineraryModification(sessionId, message, currentState, route);
            case FOLLOW_UP -> handleRequirementAndGeneration(sessionId, message, currentState, route);
            case ROUTING_FAILED -> buildRoutingFailedResponse(sessionId);
        };
    }




    //流式回答
    //最后返回的都是agentevent，这个返回是怎么在controller用，用在前端的？
    @Override
    public Flux<AgentEvent> streamChat(ChatRequest chatRequest) {
        return Flux.defer(() -> {
            String sessionId = null;
            List<AgentEvent> events = new ArrayList<>();

            try {
                //前端消息进行非空校验
                validateRequest(chatRequest);

                //获取用户的working momery
                sessionId = resolveSessionId(chatRequest);
                SessionState currentState = sessionStateStore.get(sessionId);

                //发送sse事件
                events.add(AgentEvent.status(sessionId, "正在读取会话上下文"));

                events.add(AgentEvent.status(sessionId, "正在分析意图"));
                //意图路由
                IntentRoutingDecision route = intentRoutingService.route(chatRequest.message(), currentState);

                if (route == null || route.action() == null) {
                    ChatResponse response = buildRoutingFailedResponse(sessionId);
                    events.add(AgentEvent.status(sessionId, "意图识别失败，正在请求补充说明"));
                    events.add(AgentEvent.answer(sessionId, response));
                    events.add(AgentEvent.done(sessionId));
                    return Flux.fromIterable(events);
                }

                //工具调用和知识编排
                events.add(AgentEvent.status(sessionId, "已识别任务：" + route.action()));

                boolean retrievalAttempted = route.requiresRetrieval()
                        || route.action() == IntentAction.KNOWLEDGE_QA;

                if (retrievalAttempted) {
                    events.add(AgentEvent.status(sessionId, "正在检索旅游知识库"));
                }

                ChatResponse response;

                if (route.action() == IntentAction.WEATHER_TOOL) {
                    events.add(AgentEvent.status(sessionId, "正在准备天气工具参数"));

                    ToolFlowResult toolFlowResult = executeWeatherToolFlow(
                            sessionId,
                            chatRequest.message(),
                            currentState
                    );

                    events.add(AgentEvent.toolCall(
                            sessionId,
                            "正在调用天气工具",
                            toolFlowResult.toolCallPayload()
                    ));

                    events.add(AgentEvent.toolResult(
                            sessionId,
                            toolFlowResult.toolResultPayload().success()
                                    ? "天气工具调用完成"
                                    : "天气工具调用失败，正在降级处理",
                            toolFlowResult.toolResultPayload()
                    ));

                    response = toolFlowResult.response();
                } else {
                    response = orchestrateByRoute(
                            sessionId,
                            chatRequest.message(),
                            currentState,
                            route
                    );
                }

                if (retrievalAttempted) {
                    int sourceCount = response.sources() == null ? 0 : response.sources().size();

                    events.add(AgentEvent.retrieval(
                            sessionId,
                            sourceCount == 0
                                    ? "已完成知识库检索，但暂无可展示来源"
                                    : "知识库检索完成，命中 " + sourceCount + " 条来源",
                            response
                    ));
                }

                events.add(buildStatusEvent(response));

                if (response.type() == ChatResponseType.ITINERARY
                        || response.type() == ChatResponseType.ITINERARY_UPDATED) {
                    events.add(AgentEvent.itinerary(sessionId, response));
                } else {
                    events.add(AgentEvent.answer(sessionId, response));
                }

                sessionStateStore.save(
                        sessionId,
                        currentState.merge(response.requirement(), response.itinerary())
                );

                events.add(AgentEvent.done(sessionId));
                return Flux.fromIterable(events);
            } catch (Exception ex) {
                String errorSessionId = sessionId != null ? sessionId : resolveSessionId(chatRequest);
                return Flux.just(AgentEvent.error(errorSessionId, "处理失败：" + ex.getMessage()));
            }
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


    private ChatResponse handleRequirementAndGeneration(
            String sessionId,
            String message,
            SessionState currentState,
            IntentRoutingDecision route
    ) {
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

        List<SourceReferenceDto> sources = List.of();
        String ragContext = "";

        //换成知识库流service
        if (route.requiresRetrieval()) {
            RagRetrievalFlowResult retrieval = ragRetrievalFlowService.retrieve(
                    message,
                    route,
                    merged.destination(),
                    5
            );

            if (retrieval.allowed()) {
                sources = retrieval.sources();
                ragContext = retrieval.context();
            }
        }




        Itinerary itinerary = generator.generate(merged, ragContext);

        return new ChatResponse(
                sessionId,
                ChatResponseType.ITINERARY,
                "已为你生成最小行程结果。",
                merged,
                List.of(),
                null,
                itinerary,
                sources
        );
    }

    private ChatResponse buildRoutingFailedResponse(String sessionId) {
        return new ChatResponse(
                sessionId,
                ChatResponseType.FOLLOW_UP,
                "我还不确定你是想查旅游知识、查实时信息、生成行程，还是修改已有行程。",
                null,
                List.of("intent"),
                "你可以换个说法告诉我：是想查攻略、查天气/票价/路线，还是让我生成或修改行程？",
                null,
                List.of()
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
    private ChatResponse handleKnowledgeAnswer(String sessionId, String message, SessionState currentState, IntentRoutingDecision route) {
        //String city = extractCityFromMessage(message);
        String city=route.city();

        if (!StringUtils.hasText(city)
                && currentState.requirement() != null
                && StringUtils.hasText(currentState.requirement().destination())) {
            city = currentState.requirement().destination();
        }

        // 先只按 city 过滤，不按 route.topic 精确过滤。
        // route.topic 是模型生成的自然语义标签，不一定等于入库 metadata 的 category。
        String fallbackCity = currentState.requirement() == null
                ? null
                : currentState.requirement().destination();

        RagRetrievalFlowResult retrieval = ragRetrievalFlowService.retrieve(
                message,
                route,
                fallbackCity,
                5
        );

        if (!retrieval.allowed()) {
            return buildRagRejectedResponse(sessionId, retrieval.rejectionReason());
        }

        String answer = ragAnswerGenerator.answer(message, retrieval.context());


        return new ChatResponse(
                sessionId,
                ChatResponseType.KNOWLEDGE_ANSWER,
                answer,
                null,
                List.of(),
                null,
                null,
                retrieval.sources()
        );
    }


    private ChatResponse handleItineraryModification(String sessionId, String message, SessionState currentState,IntentRoutingDecision route) {
        TripRequirement extracted = extractWithContext(message, currentState);
        TripRequirement merged = mergeRequirement(currentState.requirement(), extracted);

        List<RagRetrievalResult> results = List.of();
        List<SourceReferenceDto> sources = List.of();
        String modifierMessage = message;

        //如果需要修改流程
        if (route.requiresRetrieval()) {
            String city = StringUtils.hasText(route.city()) ? route.city() : merged.destination();

            RagRetrievalFlowResult retrieval = ragRetrievalFlowService.retrieve(
                    message,
                    route,
                    city,
                    5
            );

            if (retrieval.allowed()) {
                sources = retrieval.sources();

                if (StringUtils.hasText(retrieval.context())) {
                    modifierMessage = message + "\n\n补充参考知识：\n" + retrieval.context();
                }
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


    private ChatResponse buildRagRejectedResponse(String sessionId, String reason) {
        return new ChatResponse(
                sessionId,
                ChatResponseType.KNOWLEDGE_ANSWER,
                "我检索了知识库，但当前结果不足以作为可靠依据：" + reason
                        + "。你可以补充城市、天数或具体玩法主题，我再帮你查得更准。",
                null,
                List.of(),
                null,
                null,
                List.of()
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
