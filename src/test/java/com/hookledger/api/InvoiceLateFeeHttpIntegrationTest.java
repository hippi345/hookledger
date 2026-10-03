package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoiceLateFeeRepository;
import com.hookledger.repository.InvoicePaymentRepository;
import com.hookledger.repository.InvoiceRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PeriodCloseRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceLateFeeHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate AS_OF = LocalDate.parse("2026-06-15");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceCreditNoteRepository invoiceCreditNoteRepository;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepository;

    @Autowired
    private InvoiceLateFeeRepository invoiceLateFeeRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

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
    void overdueInvoiceGetsOneBalancedLateFeeChargeWithoutChangingOpenBalance() {
        InvoiceResponse invoice = createInvoice("Late Fee Co", AS_OF.minusDays(10), "usd", 5000);

        ResponseEntity<InvoiceLateFeeResponse> firstFee = postLateFee(invoice.id(), 250, AS_OF);
        assertThat(firstFee.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstFee.getBody()).isNotNull();
        assertThat(ledgerEventRepository.count()).isEqualTo(1);

        var charge = ledgerEventRepository.findById(firstFee.getBody().ledgerEventId()).orElseThrow();
        assertThat(charge.getAmountMinor()).isEqualTo(250);
        assertThat(charge.getDebitMinor()).isEqualTo(250);
        assertThat(charge.getCreditMinor()).isEqualTo(-250);
        assertThat(charge.getDebitMinor() + charge.getCreditMinor()).isZero();

        ResponseEntity<InvoiceAgingResponse> aging =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(aging.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aging.getBody()).isNotNull();
        assertThat(aging.getBody().currencies()).hasSize(1);
        assertThat(aging.getBody().currencies().get(0).grandTotalAmountMinor()).isEqualTo(5000);

        ResponseEntity<String> duplicate = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/late-fee",
                new CreateInvoiceLateFeeRequest(100, AS_OF),
                String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ledgerEventRepository.count()).isEqualTo(1);
    }

    @Test
    void notYetDueInvoiceRejectsLateFee() {
        InvoiceResponse invoice = createInvoice("Future Due Co", AS_OF, "usd", 3000);

        ResponseEntity<String> rejected = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/late-fee",
                new CreateInvoiceLateFeeRequest(50, AS_OF),
                String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ledgerEventRepository.count()).isZero();
        assertThat(invoiceLateFeeRepository.count()).isZero();
    }

    private ResponseEntity<InvoiceLateFeeResponse> postLateFee(
            String invoiceId, long feeMinor, LocalDate asOf) {
        return restTemplate.postForEntity(
                "/api/invoices/" + invoiceId + "/late-fee",
                new CreateInvoiceLateFeeRequest(feeMinor, asOf),
                InvoiceLateFeeResponse.class);
    }

    private InvoiceResponse createInvoice(String customer, LocalDate dueDate, String currency, long amountMinor) {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                customer, null, dueDate, currency, List.of(new InvoiceLineItemRequest("Service", amountMinor)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        return created.getBody();
    }
}
