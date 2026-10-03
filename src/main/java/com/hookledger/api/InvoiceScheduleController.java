package com.hookledger.api;

import com.hookledger.service.InvoiceScheduleService;
import com.hookledger.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoice-schedules")
@Tag(name = "Invoice schedules")
public class InvoiceScheduleController {

    private final InvoiceScheduleService invoiceScheduleService;

    public InvoiceScheduleController(InvoiceScheduleService invoiceScheduleService) {
        this.invoiceScheduleService = invoiceScheduleService;
    }

    @PostMapping
    @Operation(summary = "Create a recurring monthly invoice schedule (does not post to the ledger)")
    public ResponseEntity<InvoiceScheduleResponse> create(@RequestBody CreateInvoiceScheduleRequest request) {
        var lineItems = request.lineItems() == null
                ? List.<InvoiceService.LineItemInput>of()
                : request.lineItems().stream()
                        .map(item -> new InvoiceService.LineItemInput(
                                item.description(),
                                item.amountMinor(),
                                item.taxRateBasisPoints() != null ? item.taxRateBasisPoints() : 0))
                        .toList();
        var schedule = invoiceScheduleService.create(
                request.customerName(),
                request.customerAddress(),
                request.currency(),
                request.interval(),
                lineItems);
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceScheduleResponse.from(schedule));
    }

    @PostMapping("/{scheduleId}/generate")
    @Operation(summary = "Generate the next invoice from a schedule (does not post to the ledger until paid)")
    public ResponseEntity<InvoiceResponse> generate(@PathVariable String scheduleId) {
        return invoiceScheduleService
                .generate(scheduleId)
                .map(invoice -> ResponseEntity.status(HttpStatus.CREATED).body(InvoiceResponse.from(invoice)))
                .orElse(ResponseEntity.notFound().build());
    }
}
