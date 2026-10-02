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
@RequestMapping("/api/payouts")
@Tag(name = "Payouts")
public class PayoutSettlementController {

    private final LedgerService ledgerService;

    public PayoutSettlementController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{payoutId}/charges")
    @Operation(summary = "Walk settlement graph from a payout to its source charges")
    public List<EventResponse> sourceCharges(@PathVariable String payoutId) {
        return ledgerService.payoutSourceCharges(payoutId).stream().map(EventResponse::from).toList();
    }
}
