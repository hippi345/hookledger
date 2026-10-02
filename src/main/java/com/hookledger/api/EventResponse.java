package com.hookledger.api;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import java.time.Instant;

public record EventResponse(
        String id,
        MoneyEventType type,
        long amountMinor,
        String currency,
        Instant receivedAt,
        int replayCount) {

    public static EventResponse from(LedgerEvent event) {
        return new EventResponse(
                event.getEventId(),
                event.getType(),
                event.getAmountMinor(),
                event.getCurrency(),
                event.getReceivedAt(),
                event.getReplayCount());
    }
}
