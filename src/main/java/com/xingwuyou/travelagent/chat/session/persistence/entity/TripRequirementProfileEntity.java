package com.xingwuyou.travelagent.chat.session.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "trip_requirement_profile")
public class TripRequirementProfileEntity {
    @Id
    // TripRequirementProfileEntity
    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "trip_days")
    public Integer tripDays;

    @Column(name = "pace_preference")
    public String pacePreference;

    @Column(name = "interests_json", columnDefinition = "json")
    public String interestsJson;

    @Column(name = "start_date")
    public String startDate;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(name = "destination")
    public String destination;

    @Column(name = "budget")
    public String budget;
}