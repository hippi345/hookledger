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
class InvoiceDetailHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void getInvoiceReturnsOpenBalanceCreditsAndPayments() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "Detail Co",
                null,
                LocalDate.parse("2026-06-01"),
                "usd",
                List.of(new InvoiceLineItemRequest("Service", 5000, null)));

        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = created.getBody().id();

        assertThat(restTemplate
                        .postForEntity(
                                "/api/invoices/" + invoiceId + "/credits",
                                new CreateInvoiceCreditRequest(1000, "usd"),
                                InvoiceCreditNoteResponse.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(restTemplate
                        .postForEntity(
                                "/api/invoices/" + invoiceId + "/payments",
                                new CreateInvoicePaymentRequest(1500, "usd"),
                                InvoicePaymentResponse.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<InvoiceDetailResponse> detail =
                restTemplate.getForEntity("/api/invoices/" + invoiceId, InvoiceDetailResponse.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).isNotNull();
        assertThat(detail.getBody().openBalanceMinor()).isEqualTo(2500);
        assertThat(detail.getBody().credits()).hasSize(1);
        assertThat(detail.getBody().payments()).hasSize(1);
        assertThat(detail.getBody().invoice().customerName()).isEqualTo("Detail Co");
    }

    @Test
    void getUnknownInvoiceReturns404() {
        ResponseEntity<InvoiceDetailResponse> detail =
                restTemplate.getForEntity("/api/invoices/does-not-exist", InvoiceDetailResponse.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
