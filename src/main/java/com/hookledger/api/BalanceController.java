package com.hookledger.api;

import com.hookledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

    @GetMapping("/totals")
    @Operation(summary = "Running balance in minor units for every currency that appears in the ledger")
    public CurrencyTotalsResponse totalsByCurrency() {
        List<CurrencyTotalResponse> totals = ledgerService.balanceTotalsByCurrency().stream()
                .map(row -> new CurrencyTotalResponse(row.currency(), row.balanceMinor()))
                .toList();
        return new CurrencyTotalsResponse(totals);
    }

    @GetMapping("/{currency}")
    @Operation(summary = "Per-currency balance in minor units (charges minus refunds; payouts excluded)")
    public BalanceResponse balance(@PathVariable String currency) {
        long balanceMinor = ledgerService.balanceMinorForCurrency(currency);
        return new BalanceResponse(currency.toUpperCase(), balanceMinor);
    }
}
