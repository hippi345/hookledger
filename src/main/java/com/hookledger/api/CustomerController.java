package com.hookledger.api;

import com.hookledger.service.CustomerStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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

    public CustomerController(CustomerStatementService customerStatementService) {
        this.customerStatementService = customerStatementService;
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
}
