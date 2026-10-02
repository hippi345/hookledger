package com.hookledger.api;

import com.hookledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/by-timestamp")
    @Operation(summary = "Get one stored event by exact receivedAt timestamp (binary search)")
    public ResponseEntity<EventResponse> getByTimestamp(
            @RequestParam("at") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedAt) {
        return ledgerService
                .findEventByTimestamp(receivedAt)
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get one stored money event by id")
    public ResponseEntity<EventResponse> get(@PathVariable String eventId) {
        return ledgerService
                .getEvent(eventId)
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/replay/undo")
    @Operation(summary = "Undo the last replay (stack)")
    public ResponseEntity<EventResponse> undoReplay() {
        return ledgerService
                .undoLastReplay()
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElse(ResponseEntity.notFound().build());
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
