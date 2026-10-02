package com.hookledger.api;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.EventStateFlags;
import com.hookledger.domain.MoneyEventType;
import java.time.Instant;
import java.util.List;

public record EventResponse(
        String id,
        MoneyEventType type,
        long amountMinor,
        long debitMinor,
        long creditMinor,
        String currency,
        Instant receivedAt,
        int replayCount,
        int stateFlags,
        boolean signed,
        boolean replayed,
        boolean duplicate,
        String refundChargeId,
        List<String> payoutChargeIds) {

    public static EventResponse from(LedgerEvent event) {
        int flags = event.getStateFlags();
        return new EventResponse(
                event.getEventId(),
                event.getType(),
                event.getAmountMinor(),
                event.getDebitMinor(),
                event.getCreditMinor(),
                event.getCurrency(),
                event.getReceivedAt(),
                event.getReplayCount(),
                flags,
                EventStateFlags.isSigned(flags),
                EventStateFlags.isReplayed(flags),
                EventStateFlags.isDuplicate(flags),
                event.getRefundChargeId(),
                event.getPayoutChargeIds());
    }
}
