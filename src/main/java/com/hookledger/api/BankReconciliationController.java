package com.hookledger.api;

import com.hookledger.service.BankReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bank-lines")
@Tag(name = "Bank reconciliation")
public class BankReconciliationController {

    private final BankReconciliationService bankReconciliationService;

    public BankReconciliationController(BankReconciliationService bankReconciliationService) {
        this.bankReconciliationService = bankReconciliationService;
    }

    @PostMapping
    @Operation(summary = "Record a bank statement line (stored permanently)")
    public ResponseEntity<BankLineResponse> post(@RequestBody PostBankLineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BankLineResponse.from(bankReconciliationService.postLine(
                        request.amountMinor(), request.currency(), request.date())));
    }

    @GetMapping("/unmatched")
    @Operation(summary = "List bank lines not yet matched to a ledger entry")
    public List<BankLineResponse> listUnmatched() {
        return bankReconciliationService.listUnmatched().stream()
                .map(BankLineResponse::from)
                .toList();
    }

    @PostMapping("/{bankLineId}/match")
    @Operation(summary = "Match a bank line to one ledger entry (same amount and currency)")
    public ResponseEntity<BankLineResponse> match(
            @PathVariable String bankLineId, @RequestBody MatchBankLineRequest request) {
        return bankReconciliationService
                .match(bankLineId, request.ledgerEventId())
                .map(line -> ResponseEntity.ok(BankLineResponse.from(line)))
                .orElse(ResponseEntity.notFound().build());
    }
}
