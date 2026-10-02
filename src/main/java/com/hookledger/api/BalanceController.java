package com.hookledger.api;

import com.hookledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/balance")
@Tag(name = "Balance")
public class BalanceController {

    private final LedgerService ledgerService;

    public BalanceController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{currency}")
    @Operation(summary = "Per-currency balance in minor units (charges minus refunds; payouts excluded)")
    public BalanceResponse balance(@PathVariable String currency) {
        long balanceMinor = ledgerService.balanceMinorForCurrency(currency);
        return new BalanceResponse(currency.toUpperCase(), balanceMinor);
    }
}
