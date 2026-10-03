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
class InvoiceAgingCsvHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void agingCsvMatchesJsonBucketsForAsOf() {
        InvoiceResponse late = createInvoice("Csv Co", AS_OF.minusDays(25), "usd", 1500);

        ResponseEntity<byte[]> csv =
                restTemplate.getForEntity("/api/invoices/aging.csv?asOf=" + AS_OF, byte[].class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getBody()).isNotNull();

        String text = new String(csv.getBody());
        assertThat(text).startsWith("customer,invoice_id,due_date,currency,bucket,open_amount_minor\n");
        assertThat(text).contains("Csv Co," + late.id() + ",2026-05-21,USD,30,1500");
    }

    private InvoiceResponse createInvoice(String customer, LocalDate dueDate, String currency, long amountMinor) {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                customer, null, dueDate, currency, List.of(new InvoiceLineItemRequest("Service", amountMinor, null)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        return created.getBody();
    }
}
