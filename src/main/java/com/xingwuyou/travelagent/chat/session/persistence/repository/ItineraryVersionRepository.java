package com.xingwuyou.travelagent.chat.session.persistence.repository;

import com.xingwuyou.travelagent.chat.session.persistence.entity.ItineraryVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ItineraryVersionRepository extends JpaRepository<ItineraryVersionEntity, Long> {
    int countBySessionId(String sessionId);
    Optional<ItineraryVersionEntity> findTopBySessionIdOrderByVersionNoDesc(String sessionId);
}