package com.hookledger.api;

import com.hookledger.service.CustomerStatementService;
import java.time.LocalDate;
import java.util.List;

public record CustomerStatementResponse(
        String customer,
        String currency,
        LocalDate from,
        LocalDate to,
        long startingBalanceMinor,
        long endingBalanceMinor,
        List<CustomerStatementLineResponse> lines) {

    public static CustomerStatementResponse from(CustomerStatementService.CustomerStatement statement) {
        return new CustomerStatementResponse(
                statement.customer(),
                statement.currency(),
                statement.from(),
                statement.to(),
                statement.startingBalanceMinor(),
                statement.endingBalanceMinor(),
                statement.lines().stream().map(CustomerStatementLineResponse::from).toList());
    }
}
