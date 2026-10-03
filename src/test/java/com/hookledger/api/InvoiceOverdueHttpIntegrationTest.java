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
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceOverdueHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void overdueListsUnpaidInvoicesPastDueGroupedByCustomer() {
        String customer = "Overdue Customer Co";
        LocalDate overdueDueDate = AS_OF.minusDays(10);
        InvoiceResponse overdue = createInvoice(customer, overdueDueDate, "usd", 5000);
        createInvoice(customer, AS_OF.plusDays(1), "usd", 2000);
        InvoiceResponse paidPastDue = createInvoice("Other Co", AS_OF.minusDays(5), "usd", 3000);

        ResponseEntity<InvoiceResponse> paidResponse =
                restTemplate.postForEntity("/api/invoices/" + paidPastDue.id() + "/pay", null, InvoiceResponse.class);
        assertThat(paidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<InvoiceOverdueResponse> overdueResponse =
                restTemplate.getForEntity("/api/invoices/overdue?asOf=" + AS_OF, InvoiceOverdueResponse.class);
        assertThat(overdueResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overdueResponse.getBody()).isNotNull();
        assertThat(overdueResponse.getBody().asOf()).isEqualTo(AS_OF);
        assertThat(overdueResponse.getBody().customers()).hasSize(1);

        InvoiceOverdueResponse.CustomerOverdueResponse group =
                overdueResponse.getBody().customers().get(0);
        assertThat(group.customer()).isEqualTo(customer);
        assertThat(group.invoices()).hasSize(1);

        InvoiceOverdueResponse.InvoiceOverdueLineResponse line = group.invoices().get(0);
        assertThat(line.id()).isEqualTo(overdue.id());
        assertThat(line.dueDate()).isEqualTo(overdueDueDate);
        assertThat(line.currency()).isEqualTo("USD");
        assertThat(line.openAmountMinor()).isEqualTo(5000);
        assertThat(line.daysPastDue()).isEqualTo(ChronoUnit.DAYS.between(overdueDueDate, AS_OF));

        assertThat(group.totalsByCurrency()).containsExactly(new InvoiceOverdueResponse.CurrencyTotalResponse("USD", 5000));
        assertThat(ledgerEventRepository.count()).isEqualTo(1);
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
