package com.xingwuyou.travelagent.chat.session.persistence.repository;

import com.xingwuyou.travelagent.chat.session.persistence.entity.TripRequirementProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRequirementProfileRepository extends JpaRepository<TripRequirementProfileEntity, String> {
}