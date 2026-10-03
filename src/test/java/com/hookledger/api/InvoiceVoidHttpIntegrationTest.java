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
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceVoidHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void voidRemovesInvoiceFromAgingOverdueAndStatementWithoutLedgerPost() {
        InvoiceResponse invoice = createInvoice("Void Me Co", AS_OF.minusDays(20), "usd", 4000);
        assertThat(postCredit(invoice.id(), 1000, "usd").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(invoiceCreditNoteRepository.count()).isEqualTo(1);

        ResponseEntity<InvoiceResponse> voided =
                restTemplate.postForEntity("/api/invoices/" + invoice.id() + "/void", null, InvoiceResponse.class);
        assertThat(voided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(voided.getBody()).isNotNull();
        assertThat(voided.getBody().status()).isEqualTo(InvoiceStatus.voided);
        assertThat(ledgerEventRepository.count()).isZero();
        assertThat(invoiceCreditNoteRepository.count()).isZero();

        ResponseEntity<InvoiceAgingResponse> aging =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(aging.getBody()).isNotNull();
        assertThat(aging.getBody().currencies()).isEmpty();

        ResponseEntity<InvoiceOverdueResponse> overdue =
                restTemplate.getForEntity("/api/invoices/overdue?asOf=" + AS_OF, InvoiceOverdueResponse.class);
        assertThat(overdue.getBody()).isNotNull();
        assertThat(overdue.getBody().customers()).isEmpty();

        String statementUrl = "/api/customers/Void%20Me%20Co/statement?currency=usd&from=2026-01-01&to=2026-12-31";
        ResponseEntity<CustomerStatementResponse> statement =
                restTemplate.getForEntity(statementUrl, CustomerStatementResponse.class);
        assertThat(statement.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statement.getBody()).isNotNull();
        assertThat(statement.getBody().lines()).isEmpty();
        assertThat(statement.getBody().startingBalanceMinor()).isZero();
        assertThat(statement.getBody().endingBalanceMinor()).isZero();
    }

    @Test
    void voidRejectsInvoiceWithPayment() {
        InvoiceResponse invoice = createInvoice("Paid Partial Co", AS_OF, "usd", 2000);
        assertThat(restTemplate
                        .postForEntity(
                                "/api/invoices/" + invoice.id() + "/payments",
                                new CreateInvoicePaymentRequest(500, "usd"),
                                InvoicePaymentResponse.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> rejected =
                restTemplate.postForEntity("/api/invoices/" + invoice.id() + "/void", null, String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<InvoiceCreditNoteResponse> postCredit(String invoiceId, long amountMinor, String currency) {
        return restTemplate.postForEntity(
                "/api/invoices/" + invoiceId + "/credits",
                new CreateInvoiceCreditRequest(amountMinor, currency),
                InvoiceCreditNoteResponse.class);
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
