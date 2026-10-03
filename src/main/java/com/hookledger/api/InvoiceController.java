package com.hookledger.api;

import com.hookledger.service.InvoiceAgingService;
import com.hookledger.service.InvoiceOverdueService;
import com.hookledger.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
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
    private final InvoiceOverdueService invoiceOverdueService;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoiceAgingService invoiceAgingService,
            InvoiceOverdueService invoiceOverdueService) {
        this.invoiceService = invoiceService;
        this.invoiceAgingService = invoiceAgingService;
        this.invoiceOverdueService = invoiceOverdueService;
    }

    @GetMapping
    @Operation(summary = "List invoices with optional customer, status, and currency filters (paginated)")
    public InvoiceListResponse list(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return InvoiceListResponse.from(invoiceService.listInvoices(customer, status, currency, limit, offset));
    }

    @GetMapping("/aging")
    @Operation(summary = "Accounts-receivable aging for unpaid invoices (does not post to the ledger)")
    public InvoiceAgingResponse aging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        return InvoiceAgingResponse.from(invoiceAgingService.buildReport(effectiveAsOf));
    }

    @GetMapping(value = "/aging.csv", produces = "text/csv")
    @Operation(summary = "CSV export of the invoice aging report (optional asOf date, default today)")
    public ResponseEntity<byte[]> agingCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        StringBuilder csv = new StringBuilder();
        csv.append("customer,invoice_id,due_date,currency,bucket,open_amount_minor\n");
        for (var line : invoiceAgingService.csvLines(effectiveAsOf)) {
            csv.append(line.customer())
                    .append(',')
                    .append(line.invoiceId())
                    .append(',')
                    .append(line.dueDate())
                    .append(',')
                    .append(line.currency())
                    .append(',')
                    .append(line.bucket())
                    .append(',')
                    .append(line.openAmountMinor())
                    .append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-aging.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Overdue unpaid invoices grouped by customer (does not post to the ledger)")
    public InvoiceOverdueResponse overdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        return InvoiceOverdueResponse.from(invoiceOverdueService.buildReport(effectiveAsOf));
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

    @PostMapping("/{invoiceId}/credits")
    @Operation(summary = "Apply a credit note to an unpaid invoice (does not post to the ledger)")
    public ResponseEntity<InvoiceCreditNoteResponse> applyCredit(
            @PathVariable String invoiceId, @RequestBody CreateInvoiceCreditRequest request) {
        return invoiceService
                .applyCredit(invoiceId, request.amountMinor(), request.currency())
                .map(credit -> ResponseEntity.status(HttpStatus.CREATED).body(InvoiceCreditNoteResponse.from(credit)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{invoiceId}/payments")
    @Operation(summary = "Apply a partial payment and post a balanced charge to the ledger")
    public ResponseEntity<InvoicePaymentResponse> applyPayment(
            @PathVariable String invoiceId, @RequestBody CreateInvoicePaymentRequest request) {
        return invoiceService
                .applyPayment(invoiceId, request.amountMinor(), request.currency())
                .map(payment -> ResponseEntity.status(HttpStatus.CREATED).body(InvoicePaymentResponse.from(payment)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{invoiceId}/late-fee")
    @Operation(summary = "Assess a one-time late fee on an overdue unpaid invoice and post a balanced charge")
    public ResponseEntity<InvoiceLateFeeResponse> applyLateFee(
            @PathVariable String invoiceId, @RequestBody CreateInvoiceLateFeeRequest request) {
        return invoiceService
                .applyLateFee(invoiceId, request.feeMinor(), request.asOf())
                .map(lateFee -> ResponseEntity.status(HttpStatus.CREATED).body(InvoiceLateFeeResponse.from(lateFee)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{invoiceId}/void")
    @Operation(summary = "Void an unpaid invoice (does not post to the ledger)")
    public ResponseEntity<InvoiceResponse> voidInvoice(@PathVariable String invoiceId) {
        return invoiceService
                .voidInvoice(invoiceId)
                .map(invoice -> ResponseEntity.ok(InvoiceResponse.from(invoice)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{invoiceId}/payments/{paymentId}/refund")
    @Operation(summary = "Refund a posted invoice payment with a balanced ledger reversal")
    public ResponseEntity<InvoicePaymentRefundResponse> refundPayment(
            @PathVariable String invoiceId, @PathVariable String paymentId) {
        return invoiceService
                .refundPayment(invoiceId, paymentId)
                .map(result -> ResponseEntity.ok(InvoicePaymentRefundResponse.from(result)))
                .orElse(ResponseEntity.notFound().build());
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
