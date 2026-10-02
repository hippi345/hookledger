package com.hookledger.api;

import com.hookledger.service.LedgerService;
import com.hookledger.service.LedgerService.IngestResult;
import com.hookledger.service.LedgerService.IngestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks")
public class WebhookController {

    private final LedgerService ledgerService;

    public WebhookController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @Operation(summary = "Ingest a signed money event webhook")
    public ResponseEntity<EventResponse> ingest(@RequestBody String rawPayload) {
        IngestResult result = ledgerService.ingest(rawPayload);
        HttpStatus status = result.status() == IngestStatus.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(EventResponse.from(result.event()));
    }
}
