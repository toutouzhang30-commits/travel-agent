package com.xingwuyou.travelagent.chat.session.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "itinerary_version")
public class ItineraryVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    // ItineraryVersionEntity
    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "version_no")
    public Integer versionNo;

    @Column(name = "trip_days")
    public Integer tripDays;

    @Column(name = "itinerary_json", columnDefinition = "json")
    public String itineraryJson;

    @Lob
    @Column(name = "reflection_summary", columnDefinition = "text")
    public String reflectionSummary;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "destination")
    public String destination;
}