package com.xingwuyou.travelagent.chat.session.persistence.repository;

import com.xingwuyou.travelagent.chat.session.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
}