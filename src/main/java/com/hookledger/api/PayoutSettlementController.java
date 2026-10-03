package com.hookledger.api;

import com.hookledger.service.LedgerService;
import com.hookledger.service.PayoutSplitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payouts")
@Tag(name = "Payouts")
public class PayoutSettlementController {

    private final LedgerService ledgerService;
    private final PayoutSplitService payoutSplitService;

    public PayoutSettlementController(LedgerService ledgerService, PayoutSplitService payoutSplitService) {
        this.ledgerService = ledgerService;
        this.payoutSplitService = payoutSplitService;
    }

    @GetMapping("/{payoutId}/charges")
    @Operation(summary = "Walk settlement graph from a payout to its source charges")
    public List<EventResponse> sourceCharges(@PathVariable String payoutId) {
        return ledgerService.payoutSourceCharges(payoutId).stream().map(EventResponse::from).toList();
    }

    @PostMapping("/{payoutId}/split")
    @Operation(summary = "Split a payout into the fewest available charges that sum to its amount")
    public ResponseEntity<PayoutSplitResponse> split(@PathVariable String payoutId) {
        return payoutSplitService
                .splitPayout(payoutId)
                .map(chargeIds -> ResponseEntity.ok(new PayoutSplitResponse(payoutId, chargeIds)))
                .orElse(ResponseEntity.notFound().build());
    }
}
