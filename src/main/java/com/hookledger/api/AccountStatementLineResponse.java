package com.hookledger.api;

import com.hookledger.service.AccountStatementService;
import java.time.Instant;

public record AccountStatementLineResponse(
        String eventId,
        String type,
        Instant receivedAt,
        long debitMinor,
        long creditMinor,
        String currency) {

    public static AccountStatementLineResponse from(AccountStatementService.AccountStatementLine line) {
        return new AccountStatementLineResponse(
                line.eventId(),
                line.type(),
                line.receivedAt(),
                line.debitMinor(),
                line.creditMinor(),
                line.currency());
    }
}
