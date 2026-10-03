package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.domain.InvoiceStatus;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void createPayAndDownloadPdf() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "Acme Widgets LLC",
                "100 Demo Plaza\nFictional City, FC 00000",
                LocalDate.parse("2026-04-15"),
                "usd",
                List.of(
                        new InvoiceLineItemRequest("Widget subscription", 2500),
                        new InvoiceLineItemRequest("Support hours", 750)));

        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().status()).isEqualTo(InvoiceStatus.open);
        assertThat(created.getBody().totalAmountMinor()).isEqualTo(3250);
        assertThat(ledgerEventRepository.count()).isZero();

        String invoiceId = created.getBody().id();

        ResponseEntity<InvoiceResponse> paid =
                restTemplate.postForEntity("/api/invoices/" + invoiceId + "/pay", null, InvoiceResponse.class);
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paid.getBody()).isNotNull();
        assertThat(paid.getBody().status()).isEqualTo(InvoiceStatus.paid);
        assertThat(paid.getBody().ledgerEventId()).isNotBlank();
        assertThat(ledgerEventRepository.count()).isEqualTo(1);

        var charge = ledgerEventRepository.findById(paid.getBody().ledgerEventId()).orElseThrow();
        assertThat(charge.getDebitMinor()).isEqualTo(3250);
        assertThat(charge.getCreditMinor()).isEqualTo(-3250);
        assertThat(charge.getDebitMinor() + charge.getCreditMinor()).isZero();

        ResponseEntity<byte[]> pdf = restTemplate.getForEntity("/api/invoices/" + invoiceId + "/pdf", byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getBody()).isNotNull();
        assertThat(pdf.getBody().length).isGreaterThan(100);
        assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");
    }
}
