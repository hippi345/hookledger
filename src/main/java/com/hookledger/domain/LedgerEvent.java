package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ledger_events")
public class LedgerEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 128)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MoneyEventType type;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private int replayCount;

    protected LedgerEvent() {
    }

    public LedgerEvent(
            String eventId,
            MoneyEventType type,
            long amountMinor,
            String currency,
            String rawPayload,
            Instant receivedAt) {
        this.eventId = eventId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt;
        this.replayCount = 0;
    }

    public String getEventId() {
        return eventId;
    }

    public MoneyEventType getType() {
        return type;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getReplayCount() {
        return replayCount;
    }

    public void incrementReplayCount() {
        this.replayCount++;
    }
}
