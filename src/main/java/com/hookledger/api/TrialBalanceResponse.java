package com.hookledger.api;

import com.hookledger.service.TrialBalanceService;
import java.util.List;

public record TrialBalanceResponse(
        List<TrialBalanceLineResponse> lines,
        long totalDebitMinor,
        long totalCreditMinor,
        long netMinor,
        boolean balanced) {

    public static TrialBalanceResponse from(TrialBalanceService.TrialBalance trialBalance) {
        List<TrialBalanceLineResponse> lines = trialBalance.lines().stream()
                .map(line -> new TrialBalanceLineResponse(
                        line.currency(), line.account(), line.debitMinor(), line.creditMinor()))
                .toList();
        return new TrialBalanceResponse(
                lines,
                trialBalance.totalDebitMinor(),
                trialBalance.totalCreditMinor(),
                trialBalance.netMinor(),
                trialBalance.balanced());
    }

    public record TrialBalanceLineResponse(String currency, String account, long debitMinor, long creditMinor) {}
}
