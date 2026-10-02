package com.hookledger.api;

import com.hookledger.service.AccountStatementService;
import java.time.Instant;
import java.util.List;

public record AccountStatementResponse(
        String account,
        String currency,
        Instant from,
        Instant to,
        long startingBalanceMinor,
        long endingBalanceMinor,
        List<AccountStatementLineResponse> lines) {

    public static AccountStatementResponse from(AccountStatementService.AccountStatement statement) {
        return new AccountStatementResponse(
                statement.account(),
                statement.currency(),
                statement.from(),
                statement.to(),
                statement.startingBalanceMinor(),
                statement.endingBalanceMinor(),
                statement.lines().stream().map(AccountStatementLineResponse::from).toList());
    }
}
