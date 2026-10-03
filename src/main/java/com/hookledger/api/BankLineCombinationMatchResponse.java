package com.hookledger.api;

import com.hookledger.domain.BankLine;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BankLineCombinationMatchResponse(
        String id,
        long amountMinor,
        String currency,
        LocalDate date,
        List<String> matchedChargeIds,
        Instant matchedAt) {

    public static BankLineCombinationMatchResponse from(BankLine bankLine, List<String> matchedChargeIds) {
        return new BankLineCombinationMatchResponse(
                bankLine.getBankLineId(),
                bankLine.getAmountMinor(),
                bankLine.getCurrency(),
                bankLine.getLineDate(),
                matchedChargeIds,
                bankLine.getMatchedAt());
    }
}
