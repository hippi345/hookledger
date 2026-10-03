package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.domain.InvoicePayment;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoiceLateFeeRepository;
import com.hookledger.repository.InvoicePaymentRepository;
import com.hookledger.repository.InvoiceRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PeriodCloseRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerStatementCsvHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CUSTOMER = "Csv Stmt Co";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepository;

    @Autowired
    private InvoiceCreditNoteRepository invoiceCreditNoteRepository;

    @Autowired
    private InvoiceLateFeeRepository invoiceLateFeeRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private PeriodCloseRepository periodCloseRepository;

    @BeforeEach
    void cleanData() {
        replayIdempotencyRepository.deleteAll();
        invoiceLateFeeRepository.deleteAll();
        invoicePaymentRepository.deleteAll();
        invoiceCreditNoteRepository.deleteAll();
        invoiceRepository.deleteAll();
        ledgerEventRepository.deleteAll();
        periodCloseRepository.deleteAll();
    }

    @Test
    void statementCsvMatchesJsonColumns() {
        LocalDate invoiceDate = LocalDate.parse("2026-02-05");
        Invoice invoice = saveInvoice(CUSTOMER, invoiceDate, "USD", 2000);
        InvoicePayment payment = savePayment(invoice, LocalDate.parse("2026-02-15"), 500);

        String url = UriComponentsBuilder.fromPath("/api/customers/" + CUSTOMER + "/statement.csv")
                .queryParam("currency", "USD")
                .queryParam("from", "2026-02-01")
                .queryParam("to", "2026-02-28")
                .toUriString();

        ResponseEntity<String> csv = restTemplate.getForEntity(url, String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getBody()).isNotNull();
        String[] lines = csv.getBody().trim().split("\n");
        assertThat(lines[0]).isEqualTo("date,kind,id,amount,running balance");
        assertThat(lines[1]).startsWith("2026-02-05,invoice," + invoice.getInvoiceId() + ",2000,2000");
        assertThat(lines[2]).startsWith("2026-02-15,payment," + payment.getPaymentId() + ",500,1500");
    }

    private Invoice saveInvoice(String customer, LocalDate createdDate, String currency, long totalMinor) {
        Invoice invoice = new Invoice(customer, null, createdDate.plusDays(30), currency, createdDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        invoice.addLineItem(new InvoiceLineItem(invoice, 0, "Line", totalMinor, 0));
        return invoiceRepository.saveAndFlush(invoice);
    }

    private InvoicePayment savePayment(Invoice invoice, LocalDate paymentDate, long amountMinor) {
        InvoicePayment payment = new InvoicePayment(
                invoice, amountMinor, invoice.getCurrency(), paymentDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        return invoicePaymentRepository.saveAndFlush(payment);
    }
}
