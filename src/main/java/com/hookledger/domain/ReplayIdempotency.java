package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "replay_idempotency")
public class ReplayIdempotency {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 256)
    private String idempotencyKey;

    @Column(name = "event_id", nullable = false, updatable = false, length = 128)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReplayIdempotency() {}

    public ReplayIdempotency(String idempotencyKey, String eventId, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.eventId = eventId;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
