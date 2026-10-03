package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.domain.InvoiceScheduleInterval;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoiceLateFeeRepository;
import com.hookledger.repository.InvoicePaymentRepository;
import com.hookledger.repository.InvoiceRepository;
import com.hookledger.repository.InvoiceScheduleRepository;
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
class InvoiceScheduleHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceScheduleRepository invoiceScheduleRepository;

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
        invoiceScheduleRepository.deleteAll();
        ledgerEventRepository.deleteAll();
        periodCloseRepository.deleteAll();
    }

    @Test
    void generateCreatesMonthlyInvoicesWithoutLedgerPostsUntilPaid() {
        CreateInvoiceScheduleRequest scheduleRequest = new CreateInvoiceScheduleRequest(
                "Recurring Co",
                null,
                "usd",
                InvoiceScheduleInterval.monthly,
                List.of(new InvoiceLineItemRequest("Subscription", 3000, null)));
        ResponseEntity<InvoiceScheduleResponse> scheduleCreated =
                restTemplate.postForEntity("/api/invoice-schedules", scheduleRequest, InvoiceScheduleResponse.class);
        assertThat(scheduleCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(scheduleCreated.getBody()).isNotNull();
        String scheduleId = scheduleCreated.getBody().id();

        ResponseEntity<InvoiceResponse> first =
                restTemplate.postForEntity("/api/invoice-schedules/" + scheduleId + "/generate", null, InvoiceResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();
        LocalDate firstDue = first.getBody().dueDate();
        assertThat(ledgerEventRepository.count()).isZero();

        ResponseEntity<InvoiceResponse> second =
                restTemplate.postForEntity("/api/invoice-schedules/" + scheduleId + "/generate", null, InvoiceResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().dueDate()).isEqualTo(firstDue.plusMonths(1));
        assertThat(ledgerEventRepository.count()).isZero();

        ResponseEntity<InvoiceResponse> paid = restTemplate.postForEntity(
                "/api/invoices/" + first.getBody().id() + "/pay", null, InvoiceResponse.class);
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ledgerEventRepository.count()).isEqualTo(1);
    }
}
