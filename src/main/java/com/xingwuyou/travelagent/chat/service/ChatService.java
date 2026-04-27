package com.xingwuyou.travelagent.chat.service;

import com.xingwuyou.travelagent.chat.dto.AgentEvent;
import com.xingwuyou.travelagent.chat.dto.ChatRequest;
import com.xingwuyou.travelagent.chat.dto.ChatResponse;
import reactor.core.publisher.Flux;

public interface ChatService {
    ChatResponse chat(ChatRequest chatRequest);
    Flux<AgentEvent> streamChat(ChatRequest chatRequest);
}
