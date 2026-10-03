package com.hookledger.api;

import com.hookledger.domain.BankLine;
import java.time.Instant;
import java.time.LocalDate;

public record BankLineResponse(
        String id,
        long amountMinor,
        String currency,
        LocalDate date,
        Instant createdAt,
        String matchedLedgerEventId,
        Instant matchedAt) {

    public static BankLineResponse from(BankLine line) {
        return new BankLineResponse(
                line.getBankLineId(),
                line.getAmountMinor(),
                line.getCurrency(),
                line.getLineDate(),
                line.getCreatedAt(),
                line.getMatchedLedgerEventId(),
                line.getMatchedAt());
    }
}
