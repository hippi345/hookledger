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
class CustomerSearchHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void customerSearchIsCaseInsensitivePrefixScan() {
        createInvoice("Alpha Industries", LocalDate.parse("2026-04-01"), "usd", 100);
        createInvoice("alpha workshop", LocalDate.parse("2026-04-02"), "usd", 200);
        createInvoice("Beta LLC", LocalDate.parse("2026-04-03"), "usd", 300);
        createInvoice("Alpine Gear", LocalDate.parse("2026-04-04"), "usd", 400);

        ResponseEntity<CustomerNameListResponse> all =
                restTemplate.getForEntity("/api/customers", CustomerNameListResponse.class);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).isNotNull();
        assertThat(all.getBody().customers())
                .containsExactly("Alpha Industries", "Alpine Gear", "Beta LLC", "alpha workshop");

        ResponseEntity<CustomerNameListResponse> alphaPrefix =
                restTemplate.getForEntity("/api/customers?q=al", CustomerNameListResponse.class);
        assertThat(alphaPrefix.getBody()).isNotNull();
        assertThat(alphaPrefix.getBody().customers())
                .containsExactly("Alpha Industries", "Alpine Gear", "alpha workshop");

        ResponseEntity<CustomerNameListResponse> betaPrefix =
                restTemplate.getForEntity("/api/customers?q=Be", CustomerNameListResponse.class);
        assertThat(betaPrefix.getBody()).isNotNull();
        assertThat(betaPrefix.getBody().customers()).containsExactly("Beta LLC");
    }

    private void createInvoice(String customer, LocalDate dueDate, String currency, long amountMinor) {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                customer, null, dueDate, currency, List.of(new InvoiceLineItemRequest("Service", amountMinor, null)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
