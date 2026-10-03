package com.hookledger.api;

import com.hookledger.service.CustomerStatementPdfService;
import com.hookledger.service.CustomerStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerStatementService customerStatementService;
    private final CustomerStatementPdfService customerStatementPdfService;

    public CustomerController(
            CustomerStatementService customerStatementService,
            CustomerStatementPdfService customerStatementPdfService) {
        this.customerStatementService = customerStatementService;
        this.customerStatementPdfService = customerStatementPdfService;
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
