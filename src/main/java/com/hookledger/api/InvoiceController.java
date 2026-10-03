package com.hookledger.api;

import com.hookledger.service.InvoiceAgingService;
import com.hookledger.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceAgingService invoiceAgingService;

    public InvoiceController(InvoiceService invoiceService, InvoiceAgingService invoiceAgingService) {
        this.invoiceService = invoiceService;
        this.invoiceAgingService = invoiceAgingService;
    }

    @GetMapping("/aging")
    @Operation(summary = "Accounts-receivable aging for unpaid invoices (does not post to the ledger)")
    public InvoiceAgingResponse aging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        return InvoiceAgingResponse.from(invoiceAgingService.buildReport(effectiveAsOf));
    }

    @PostMapping
    @Operation(summary = "Create an invoice (does not post to the ledger)")
    public ResponseEntity<InvoiceResponse> create(@RequestBody CreateInvoiceRequest request) {
        var lineItems = request.lineItems() == null
                ? List.<InvoiceService.LineItemInput>of()
                : request.lineItems().stream()
                        .map(item -> new InvoiceService.LineItemInput(item.description(), item.amountMinor()))
                        .toList();
        var invoice = invoiceService.create(
                request.customerName(),
                request.customerAddress(),
                request.dueDate(),
                request.currency(),
                lineItems);
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceResponse.from(invoice));
    }

    @PostMapping("/{invoiceId}/pay")
    @Operation(summary = "Mark an invoice paid and post a balanced charge to the ledger")
    public ResponseEntity<InvoiceResponse> pay(@PathVariable String invoiceId) {
        return invoiceService
                .markPaid(invoiceId)
                .map(invoice -> ResponseEntity.ok(InvoiceResponse.from(invoice)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{invoiceId}/pdf")
    @Operation(summary = "Download the invoice as a PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable String invoiceId) {
        return invoiceService
                .renderPdf(invoiceId)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + invoiceId + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }
}
