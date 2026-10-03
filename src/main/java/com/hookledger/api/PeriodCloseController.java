package com.hookledger.api;

import com.hookledger.service.PeriodCloseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/period-close")
@Tag(name = "Period close")
public class PeriodCloseController {

    private final PeriodCloseService periodCloseService;

    public PeriodCloseController(PeriodCloseService periodCloseService) {
        this.periodCloseService = periodCloseService;
    }

    @GetMapping
    @Operation(summary = "Current period lock (404 if none)")
    public ResponseEntity<PeriodCloseResponse> get() {
        return periodCloseService
                .lockedThrough()
                .map(lock -> ResponseEntity.ok(new PeriodCloseResponse(lock)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Lock the books through a date (inclusive); later events must be after this instant")
    public PeriodCloseResponse close(
            @RequestParam("through") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant through) {
        return new PeriodCloseResponse(periodCloseService.closeThrough(through));
    }
}
