package com.xingwuyou.travelagent.chat.session.persistence.repository;

import com.xingwuyou.travelagent.chat.session.persistence.entity.ConversationTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurnEntity, Long> {
    int countBySessionId(String sessionId);
    List<ConversationTurnEntity> findTop5BySessionIdOrderByTurnIndexDesc(String sessionId);
}