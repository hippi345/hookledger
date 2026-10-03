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
class InvoiceSalesTaxHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
    void lineTaxUsesIntegerHalfAwayFromZeroRoundingInInvoiceTotal() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "Tax Co",
                null,
                LocalDate.parse("2026-05-01"),
                "usd",
                List.of(new InvoiceLineItemRequest("Taxable item", 1000, 875)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().lineItems()).hasSize(1);
        assertThat(created.getBody().lineItems().get(0).taxMinor()).isEqualTo(88);
        assertThat(created.getBody().lineItems().get(0).lineTotalMinor()).isEqualTo(1088);
        assertThat(created.getBody().totalAmountMinor()).isEqualTo(1088);
    }
}
