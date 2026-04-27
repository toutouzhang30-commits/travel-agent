package com.xingwuyou.travelagent.chat.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//内存数据库，currentHashMap,存放sessionId和之前存储的状态
@Component
public class SessionStateStore {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            String generated = UUID.randomUUID().toString();
            sessions.putIfAbsent(generated, new SessionState(null, null));
            return generated;
        }
        sessions.putIfAbsent(sessionId, new SessionState(null, null));
        return sessionId;
    }

    public SessionState get(String sessionId) {
        return sessions.getOrDefault(sessionId, new SessionState(null, null));
    }

    public void save(String sessionId, SessionState state) {
        sessions.put(sessionId, state);
    }
}
