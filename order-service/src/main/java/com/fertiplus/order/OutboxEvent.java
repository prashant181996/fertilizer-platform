package com.fertiplus.order;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_pending", columnList = "published_at,created_at"))
public class OutboxEvent {
    @Id
    public String id;
    @Column(nullable = false)
    public String topic;
    @Column(nullable = false)
    public String messageKey;
    @Column(nullable = false, columnDefinition = "text")
    public String payload;
    @Column(name = "published_at")
    public Instant publishedAt;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    protected OutboxEvent() {
    }

    public OutboxEvent(String topic, String messageKey, String payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }
}
