package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_audit_entries")
public class LedgerAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 128)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private LedgerAuditAction action;

    @Column(nullable = false, updatable = false, length = 256)
    private String actor;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(columnDefinition = "text", updatable = false)
    private String detail;

    protected LedgerAuditEntry() {}

    public LedgerAuditEntry(String eventId, LedgerAuditAction action, String actor, Instant recordedAt, String detail) {
        this.eventId = eventId;
        this.action = action;
        this.actor = actor;
        this.recordedAt = recordedAt;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public LedgerAuditAction getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getDetail() {
        return detail;
    }
}
