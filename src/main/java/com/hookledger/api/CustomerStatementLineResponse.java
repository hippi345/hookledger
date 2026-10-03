package com.hookledger.api;

import com.hookledger.service.CustomerStatementService;
import java.time.LocalDate;

public record CustomerStatementLineResponse(
        LocalDate date, String type, String id, long amountMinor, long balanceMinor) {

    public static CustomerStatementLineResponse from(CustomerStatementService.CustomerStatementLine line) {
        return new CustomerStatementLineResponse(
                line.date(), line.type(), line.id(), line.amountMinor(), line.balanceMinor());
    }
}
