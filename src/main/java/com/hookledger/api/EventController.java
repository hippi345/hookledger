package com.hookledger.api;

import com.hookledger.service.LedgerService;
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
@RequestMapping("/api/events")
@Tag(name = "Events")
public class EventController {

    private final LedgerService ledgerService;

    public EventController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    @Operation(summary = "List stored money events (newest first)")
    public List<EventResponse> list() {
        return ledgerService.listEvents().stream().map(EventResponse::from).toList();
    }

    @PostMapping("/{eventId}/replay")
    @Operation(summary = "Replay a stored event by id (increments replay count)")
    public ResponseEntity<EventResponse> replay(@PathVariable String eventId) {
        return ledgerService
                .replay(eventId)
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElse(ResponseEntity.notFound().build());
    }
}
