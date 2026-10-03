package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceCreditNote;
import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.domain.InvoicePayment;
import com.hookledger.repository.InvoiceCreditNoteRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerStatementHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CUSTOMER = "Statement Co";
    private static final LocalDate FROM = LocalDate.parse("2026-02-01");
    private static final LocalDate TO = LocalDate.parse("2026-02-28");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceCreditNoteRepository invoiceCreditNoteRepository;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepository;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @Autowired
    private PeriodCloseRepository periodCloseRepository;

    @BeforeEach
    void cleanData() {
        replayIdempotencyRepository.deleteAll();
        invoicePaymentRepository.deleteAll();
        invoiceCreditNoteRepository.deleteAll();
        invoiceRepository.deleteAll();
        ledgerEventRepository.deleteAll();
        periodCloseRepository.deleteAll();
    }

    @Test
    void customerStatementBalancesAndLinesRespectDateRange() {
        saveInvoice(CUSTOMER, LocalDate.parse("2026-01-15"), "USD", 4000);

        Invoice inRangeInvoice = saveInvoice(CUSTOMER, LocalDate.parse("2026-02-05"), "USD", 2000);
        InvoiceCreditNote credit = saveCredit(inRangeInvoice, LocalDate.parse("2026-02-10"), 500);
        InvoicePayment payment = savePayment(inRangeInvoice, LocalDate.parse("2026-02-15"), 1000);

        saveInvoice(CUSTOMER, LocalDate.parse("2026-03-05"), "USD", 9000);
        saveInvoice("Other Customer", LocalDate.parse("2026-02-12"), "USD", 7777);
        saveInvoice(CUSTOMER, LocalDate.parse("2026-02-12"), "EUR", 3333);

        String url = UriComponentsBuilder.fromPath("/api/customers/{customer}/statement")
                .queryParam("currency", "usd")
                .queryParam("from", FROM)
                .queryParam("to", TO)
                .buildAndExpand(CUSTOMER)
                .toUriString();

        ResponseEntity<CustomerStatementResponse> response =
                restTemplate.getForEntity(url, CustomerStatementResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CustomerStatementResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.customer()).isEqualTo(CUSTOMER);
        assertThat(body.currency()).isEqualTo("USD");
        assertThat(body.from()).isEqualTo(FROM);
        assertThat(body.to()).isEqualTo(TO);
        assertThat(body.startingBalanceMinor()).isEqualTo(4000);
        assertThat(body.endingBalanceMinor()).isEqualTo(4500);

        assertThat(body.lines()).hasSize(3);
        assertThat(body.lines().get(0).date()).isEqualTo(LocalDate.parse("2026-02-05"));
        assertThat(body.lines().get(0).type()).isEqualTo("invoice");
        assertThat(body.lines().get(0).id()).isEqualTo(inRangeInvoice.getInvoiceId());
        assertThat(body.lines().get(0).amountMinor()).isEqualTo(2000);
        assertThat(body.lines().get(0).balanceMinor()).isEqualTo(6000);

        assertThat(body.lines().get(1).date()).isEqualTo(LocalDate.parse("2026-02-10"));
        assertThat(body.lines().get(1).type()).isEqualTo("credit");
        assertThat(body.lines().get(1).id()).isEqualTo(credit.getCreditNoteId());
        assertThat(body.lines().get(1).amountMinor()).isEqualTo(500);
        assertThat(body.lines().get(1).balanceMinor()).isEqualTo(5500);

        assertThat(body.lines().get(2).date()).isEqualTo(LocalDate.parse("2026-02-15"));
        assertThat(body.lines().get(2).type()).isEqualTo("payment");
        assertThat(body.lines().get(2).id()).isEqualTo(payment.getPaymentId());
        assertThat(body.lines().get(2).amountMinor()).isEqualTo(1000);
        assertThat(body.lines().get(2).balanceMinor()).isEqualTo(4500);
    }

    @Test
    void customerStatementPdfReturnsPdfBytes() {
        saveInvoice(CUSTOMER, LocalDate.parse("2026-01-15"), "USD", 4000);

        Invoice inRangeInvoice = saveInvoice(CUSTOMER, LocalDate.parse("2026-02-05"), "USD", 2000);
        saveCredit(inRangeInvoice, LocalDate.parse("2026-02-10"), 500);
        savePayment(inRangeInvoice, LocalDate.parse("2026-02-15"), 1000);

        String url = UriComponentsBuilder.fromPath("/api/customers/{customer}/statement.pdf")
                .queryParam("currency", "usd")
                .queryParam("from", FROM)
                .queryParam("to", TO)
                .buildAndExpand(CUSTOMER)
                .toUriString();

        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(100);
        assertThat(new String(response.getBody(), 0, 4)).isEqualTo("%PDF");
    }

    private Invoice saveInvoice(String customer, LocalDate activityDate, String currency, long totalMinor) {
        Invoice invoice = new Invoice(
                customer,
                null,
                activityDate.plusDays(30),
                currency,
                activityDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        invoice.addLineItem(new InvoiceLineItem(invoice, 0, "Line", totalMinor));
        return invoiceRepository.saveAndFlush(invoice);
    }

    private InvoiceCreditNote saveCredit(Invoice invoice, LocalDate activityDate, long amountMinor) {
        InvoiceCreditNote credit = new InvoiceCreditNote(
                invoice,
                amountMinor,
                invoice.getCurrency(),
                activityDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        return invoiceCreditNoteRepository.saveAndFlush(credit);
    }

    private InvoicePayment savePayment(Invoice invoice, LocalDate activityDate, long amountMinor) {
        InvoicePayment payment = new InvoicePayment(
                invoice,
                amountMinor,
                invoice.getCurrency(),
                activityDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        return invoicePaymentRepository.saveAndFlush(payment);
    }
}
