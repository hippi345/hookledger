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
class CustomerPaymentHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CUSTOMER = "Allocate Pay Co";

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
    void customerPaymentAllocatesOldestDueFirstWithSeparateCharges() {
        InvoiceResponse older = createInvoice(CUSTOMER, LocalDate.parse("2026-04-01"), 2000);
        createInvoice(CUSTOMER, LocalDate.parse("2026-05-01"), 3000);

        ResponseEntity<CustomerPaymentResponse> payment = restTemplate.postForEntity(
                "/api/customers/" + CUSTOMER + "/payments",
                new CreateCustomerPaymentRequest(3500, "usd"),
                CustomerPaymentResponse.class);
        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(payment.getBody()).isNotNull();
        assertThat(payment.getBody().appliedAmountMinor()).isEqualTo(3500);
        assertThat(payment.getBody().payments()).hasSize(2);
        assertThat(payment.getBody().payments().get(0).invoiceId()).isEqualTo(older.id());
        assertThat(payment.getBody().payments().get(0).amountMinor()).isEqualTo(2000);
        assertThat(payment.getBody().payments().get(1).amountMinor()).isEqualTo(1500);
        assertThat(ledgerEventRepository.count()).isEqualTo(2);
    }

    private InvoiceResponse createInvoice(String customer, LocalDate dueDate, long amountMinor) {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                customer, null, dueDate, "usd", List.of(new InvoiceLineItemRequest("Service", amountMinor, null)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }
}
