package com.hookledger.api;

import com.hookledger.service.CustomerInvoicePaymentService;
import com.hookledger.service.CustomerStatementPdfService;
import com.hookledger.service.CustomerStatementService;
import com.hookledger.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerStatementService customerStatementService;
    private final CustomerStatementPdfService customerStatementPdfService;
    private final CustomerInvoicePaymentService customerInvoicePaymentService;
    private final InvoiceService invoiceService;

    public CustomerController(
            CustomerStatementService customerStatementService,
            CustomerStatementPdfService customerStatementPdfService,
            CustomerInvoicePaymentService customerInvoicePaymentService,
            InvoiceService invoiceService) {
        this.customerStatementService = customerStatementService;
        this.customerStatementPdfService = customerStatementPdfService;
        this.customerInvoicePaymentService = customerInvoicePaymentService;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @Operation(summary = "Search customer names by prefix (case-insensitive prefix scan)")
    public CustomerNameListResponse search(@RequestParam(required = false) String q) {
        return new CustomerNameListResponse(invoiceService.searchCustomerNames(q));
    }

    @GetMapping("/{customer}/statement")
    @Operation(summary = "Customer statement for a currency and date range (minor units, read-only)")
    public CustomerStatementResponse statement(
            @PathVariable String customer,
            @RequestParam String currency,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CustomerStatementResponse.from(customerStatementService.build(customer, currency, from, to));
    }

    @GetMapping(value = "/{customer}/statement.csv", produces = "text/csv")
    @Operation(summary = "Customer statement as CSV (minor units, read-only)")
    public ResponseEntity<byte[]> statementCsv(
            @PathVariable String customer,
            @RequestParam String currency,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CustomerStatementService.CustomerStatement statement =
                customerStatementService.build(customer, currency, from, to);
        StringBuilder csv = new StringBuilder();
        csv.append("date,kind,id,amount,running balance\n");
        for (CustomerStatementService.CustomerStatementLine line : statement.lines()) {
            csv.append(line.date())
                    .append(',')
                    .append(line.type())
                    .append(',')
                    .append(line.id())
                    .append(',')
                    .append(line.amountMinor())
                    .append(',')
                    .append(line.balanceMinor())
                    .append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        String safeCustomer = statement.customer().replace("\"", "'");
        String filename = "statement-" + safeCustomer + "-" + statement.currency() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/{customer}/payments")
    @Operation(summary = "Allocate a payment across open invoices (oldest due date first)")
    public ResponseEntity<CustomerPaymentResponse> applyPayment(
            @PathVariable String customer, @RequestBody CreateCustomerPaymentRequest request) {
        var result = customerInvoicePaymentService.applyPayment(
                customer, request.amountMinor(), request.currency());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(CustomerPaymentResponse.from(result));
    }

    @GetMapping(value = "/{customer}/statement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the customer statement as a PDF (minor units, read-only)")
    public ResponseEntity<byte[]> statementPdf(
            @PathVariable String customer,
            @RequestParam String currency,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CustomerStatementService.CustomerStatement statement =
                customerStatementService.build(customer, currency, from, to);
        byte[] pdf = customerStatementPdfService.render(statement);
        String safeCustomer = statement.customer().replace("\"", "'");
        String filename = "statement-" + safeCustomer + "-" + statement.currency() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
