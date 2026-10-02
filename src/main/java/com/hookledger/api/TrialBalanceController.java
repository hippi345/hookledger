package com.hookledger.api;

import com.hookledger.service.TrialBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trial-balance")
@Tag(name = "Trial balance")
public class TrialBalanceController {

    private final TrialBalanceService trialBalanceService;

    public TrialBalanceController(TrialBalanceService trialBalanceService) {
        this.trialBalanceService = trialBalanceService;
    }

    @GetMapping
    @Operation(summary = "Per-account debits and credits; totals show whether the books still balance to zero")
    public TrialBalanceResponse trialBalance(@RequestParam(required = false) String currency) {
        return TrialBalanceResponse.from(trialBalanceService.compute(currency));
    }
}
