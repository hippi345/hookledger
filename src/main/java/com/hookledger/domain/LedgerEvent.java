package com.hookledger.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "ledger_events")
public class LedgerEvent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Column(nullable = false)
    private int stateFlags;

    @Column(name = "refund_charge_id", length = 128)
    private String refundChargeId;

    @Column(name = "payout_charge_ids", columnDefinition = "text")
    private String payoutChargeIdsJson;

    protected LedgerEvent() {}

    public LedgerEvent(
            String eventId,
            MoneyEventType type,
            long amountMinor,
            String currency,
            String rawPayload,
            Instant receivedAt,
            String refundChargeId,
            List<String> payoutChargeIds) {
        this.eventId = eventId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt;
        this.replayCount = 0;
        this.stateFlags = EventStateFlags.SIGNED;
        this.refundChargeId = refundChargeId;
        this.payoutChargeIdsJson = encodeChargeIds(payoutChargeIds);
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

    public int getStateFlags() {
        return stateFlags;
    }

    public String getRefundChargeId() {
        return refundChargeId;
    }

    public List<String> getPayoutChargeIds() {
        return decodeChargeIds(payoutChargeIdsJson);
    }

    public List<String> outgoingSettlementTargets() {
        if (type == MoneyEventType.refund && refundChargeId != null) {
            return List.of(refundChargeId);
        }
        if (type == MoneyEventType.payout) {
            return getPayoutChargeIds();
        }
        return List.of();
    }

    public void markDuplicateIngest() {
        stateFlags |= EventStateFlags.DUPLICATE;
    }

    public void recordReplay() {
        replayCount++;
        stateFlags |= EventStateFlags.REPLAYED;
    }

    public void undoLastReplay() {
        if (replayCount <= 0) {
            return;
        }
        replayCount--;
        if (replayCount == 0) {
            stateFlags &= ~EventStateFlags.REPLAYED;
        }
    }

    private static String encodeChargeIds(List<String> chargeIds) {
        if (chargeIds == null || chargeIds.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(chargeIds);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to encode payout charge ids", ex);
        }
    }

    private static List<String> decodeChargeIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to decode payout charge ids", ex);
        }
    }
}
