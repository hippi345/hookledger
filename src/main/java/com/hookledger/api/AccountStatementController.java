package com.hookledger.api;

import com.hookledger.service.AccountStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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
@RequestMapping("/api/accounts")
@Tag(name = "Account statements")
public class AccountStatementController {

    private static final DateTimeFormatter CSV_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final AccountStatementService accountStatementService;

    public AccountStatementController(AccountStatementService accountStatementService) {
        this.accountStatementService = accountStatementService;
    }

    @GetMapping("/{account}/statement")
    @Operation(summary = "Account statement for a currency and date range (minor units)")
    public AccountStatementResponse statement(
            @PathVariable String account,
            @RequestParam String currency,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return AccountStatementResponse.from(accountStatementService.build(account, currency, from, to));
    }

    @GetMapping(value = "/{account}/statement.csv", produces = "text/csv")
    @Operation(summary = "CSV export of the account statement")
    public ResponseEntity<byte[]> statementCsv(
            @PathVariable String account,
            @RequestParam String currency,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        AccountStatementService.AccountStatement statement =
                accountStatementService.build(account, currency, from, to);
        StringBuilder csv = new StringBuilder();
        csv.append("account,currency,from,to,starting_balance_minor,ending_balance_minor\n");
        csv.append(statement.account())
                .append(',')
                .append(statement.currency())
                .append(',')
                .append(CSV_INSTANT.format(statement.from()))
                .append(',')
                .append(CSV_INSTANT.format(statement.to()))
                .append(',')
                .append(statement.startingBalanceMinor())
                .append(',')
                .append(statement.endingBalanceMinor())
                .append('\n');
        csv.append("event_id,type,received_at,debit_minor,credit_minor,currency\n");
        for (AccountStatementService.AccountStatementLine line : statement.lines()) {
            csv.append(line.eventId())
                    .append(',')
                    .append(line.type())
                    .append(',')
                    .append(CSV_INSTANT.format(line.receivedAt()))
                    .append(',')
                    .append(line.debitMinor())
                    .append(',')
                    .append(line.creditMinor())
                    .append(',')
                    .append(line.currency())
                    .append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "statement-" + statement.account() + "-" + statement.currency() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
