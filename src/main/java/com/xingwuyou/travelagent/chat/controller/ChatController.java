package com.xingwuyou.travelagent.chat.controller;

import com.xingwuyou.travelagent.chat.dto.AgentEvent;
import com.xingwuyou.travelagent.chat.dto.AgentEventType;
import com.xingwuyou.travelagent.chat.dto.ChatRequest;
import com.xingwuyou.travelagent.chat.dto.ChatResponse;
import com.xingwuyou.travelagent.chat.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest){
        ChatResponse response = chatService.chat(chatRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChat(@RequestBody ChatRequest chatRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        emitter.onCompletion(() -> disposeSubscription(subscriptionRef));
        emitter.onTimeout(() -> {
            disposeSubscription(subscriptionRef);
            emitter.complete();
        });

        try {
            //调用service
            Disposable subscription = chatService.streamChat(chatRequest)
                    .subscribe(
                            event -> {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name(toEventName(event.type()))
                                            .data(event, MediaType.APPLICATION_JSON));
                                } catch (IOException e) {
                                    disposeSubscription(subscriptionRef);
                                    emitter.complete();
                                }
                            },
                            error -> handleError(chatRequest.sessionId(), error, emitter),
                            () -> emitter.complete()
                    );

            subscriptionRef.set(subscription);
        } catch (Exception e) {
            handleError(resolveSessionId(chatRequest), e, emitter);
        }

        return emitter;
    }

    //SSE事件名映射
    private static String toEventName(AgentEventType type) {
        return switch (type) {
            case STATUS -> "status";
            case RETRIEVAL -> "retrieval";
            case TOOL_CALL -> "tool_call";
            case TOOL_RESULT -> "tool_result";
            case ANSWER -> "answer";
            case ITINERARY -> "itinerary";
            case DONE -> "done";
            case ERROR -> "error";
        };
    }

    private static void handleError(String sessionId, Throwable error, SseEmitter emitter) {
        try {
            String message = error.getMessage() == null ? "stream failed" : error.getMessage();
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(AgentEvent.error(sessionId, message), MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    private void disposeSubscription(AtomicReference<Disposable> subscriptionRef) {
        Disposable disposable = subscriptionRef.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private static String resolveSessionId(ChatRequest chatRequest) {
        return chatRequest == null ? null : chatRequest.sessionId();
    }
}
