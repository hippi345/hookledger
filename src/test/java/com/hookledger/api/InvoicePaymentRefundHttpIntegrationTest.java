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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoicePaymentRefundHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void refundReversesPaymentAndRestoresOpenBalance() {
        InvoiceResponse invoice = createInvoice("Refund Co", AS_OF.minusDays(10), "usd", 5000);

        ResponseEntity<InvoicePaymentResponse> payment = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/payments",
                new CreateInvoicePaymentRequest(2000, "usd"),
                InvoicePaymentResponse.class);
        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(payment.getBody()).isNotNull();
        assertThat(ledgerEventRepository.count()).isEqualTo(1);

        ResponseEntity<InvoiceAgingResponse> beforeRefund =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(currencyAging(beforeRefund.getBody(), "USD").grandTotalAmountMinor()).isEqualTo(3000);

        ResponseEntity<InvoicePaymentRefundResponse> refunded = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/payments/" + payment.getBody().id() + "/refund",
                null,
                InvoicePaymentRefundResponse.class);
        assertThat(refunded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refunded.getBody()).isNotNull();
        assertThat(refunded.getBody().reversalLedgerEventId()).endsWith("_reversal");
        assertThat(ledgerEventRepository.count()).isEqualTo(2);

        ResponseEntity<InvoiceAgingResponse> afterRefund =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(currencyAging(afterRefund.getBody(), "USD").grandTotalAmountMinor()).isEqualTo(5000);

        ResponseEntity<String> secondRefund = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/payments/" + payment.getBody().id() + "/refund",
                null,
                String.class);
        assertThat(secondRefund.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ledgerEventRepository.count()).isEqualTo(2);
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

    private static InvoiceAgingResponse.CurrencyAgingResponse currencyAging(
            InvoiceAgingResponse aging, String currency) {
        if (aging == null) {
            return null;
        }
        Map<String, InvoiceAgingResponse.CurrencyAgingResponse> byCurrency = aging.currencies()
                .stream()
                .collect(Collectors.toMap(InvoiceAgingResponse.CurrencyAgingResponse::currency, Function.identity()));
        return byCurrency.get(currency);
    }
}
