package com.xingwuyou.travelagent.chat.session.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "conversation_turn")
public class ConversationTurnEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    // ConversationTurnEntity
    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "turn_index")
    public Integer turnIndex;

    @Lob
    @Column(name = "user_message", columnDefinition = "text")
    public String userMessage;

    @Lob
    @Column(name = "assistant_message", columnDefinition = "text")
    public String assistantMessage;

    @Column(name = "route_action")
    public String routeAction;

    @Column(name = "output_mode")
    public String outputMode;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;
}