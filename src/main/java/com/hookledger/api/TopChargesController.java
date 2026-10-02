package com.hookledger.api;

import com.hookledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/charges")
@Tag(name = "Charges")
public class TopChargesController {

    private final LedgerService ledgerService;

    public TopChargesController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/top")
    @Operation(summary = "Top charge events by amount (priority queue, caller limit)")
    public List<EventResponse> top(@RequestParam(defaultValue = "10") int limit) {
        return ledgerService.topChargesByAmount(limit).stream().map(EventResponse::from).toList();
    }
}
