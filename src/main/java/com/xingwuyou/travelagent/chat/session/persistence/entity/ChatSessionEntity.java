package com.xingwuyou.travelagent.chat.session.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {
    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "status")
    public String status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(name = "last_active_at")
    public OffsetDateTime lastActiveAt;
}