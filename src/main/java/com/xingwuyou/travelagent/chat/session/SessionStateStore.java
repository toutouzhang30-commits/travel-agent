package com.xingwuyou.travelagent.chat.session;

import com.xingwuyou.travelagent.chat.session.model.SessionState;
import com.xingwuyou.travelagent.chat.session.persistence.service.PersistentSessionMemoryService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//内存数据库，currentHashMap,存放sessionId和之前存储的状态
//改成先内存再MySQl
@Component
public class SessionStateStore {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final PersistentSessionMemoryService persistentMemoryService;

    public SessionStateStore(PersistentSessionMemoryService persistentMemoryService) {
        this.persistentMemoryService = persistentMemoryService;
    }

    public String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            String generated = UUID.randomUUID().toString();
            sessions.putIfAbsent(generated, new SessionState(null, null));
            return generated;
        }

        sessions.computeIfAbsent(
                sessionId,
                id -> persistentMemoryService.restore(id).orElse(new SessionState(null, null))
        );
        return sessionId;
    }

    public SessionState get(String sessionId) {
        return sessions.computeIfAbsent(
                sessionId,
                id -> persistentMemoryService.restore(id).orElse(new SessionState(null, null))
        );
    }

    public void save(String sessionId, SessionState state) {
        sessions.put(sessionId, state);
    }
}
