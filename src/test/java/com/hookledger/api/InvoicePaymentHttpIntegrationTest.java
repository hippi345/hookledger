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
class InvoicePaymentHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void partialPaymentReducesAgingAndPostsMatchingCharge() {
        InvoiceResponse invoice = createInvoice("Partial Pay Co", AS_OF.minusDays(10), "usd", 5000);

        ResponseEntity<InvoicePaymentResponse> firstPayment = postPayment(invoice.id(), 2000, "usd");
        assertThat(firstPayment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstPayment.getBody()).isNotNull();
        assertThat(ledgerEventRepository.count()).isEqualTo(1);

        var charge = ledgerEventRepository.findById(firstPayment.getBody().ledgerEventId()).orElseThrow();
        assertThat(charge.getAmountMinor()).isEqualTo(2000);
        assertThat(charge.getDebitMinor()).isEqualTo(2000);
        assertThat(charge.getCreditMinor()).isEqualTo(-2000);

        ResponseEntity<InvoiceAgingResponse> aging =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(aging.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aging.getBody()).isNotNull();

        InvoiceAgingResponse.CurrencyAgingResponse usd = currencyAging(aging.getBody(), "USD");
        assertThat(usd.days1To30().totalAmountMinor()).isEqualTo(3000);
        assertThat(usd.days1To30().invoices()).hasSize(1);
        assertThat(usd.days1To30().invoices().get(0).openAmountMinor()).isEqualTo(3000);
        assertThat(usd.grandTotalAmountMinor()).isEqualTo(3000);
    }

    @Test
    void payingRemainderRemovesInvoiceFromAging() {
        InvoiceResponse invoice = createInvoice("Finish Pay Co", AS_OF.minusDays(5), "usd", 4000);

        assertThat(postPayment(invoice.id(), 1500, "usd").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postPayment(invoice.id(), 2500, "usd").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ledgerEventRepository.count()).isEqualTo(2);

        ResponseEntity<InvoiceAgingResponse> aging =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(aging.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aging.getBody()).isNotNull();
        assertThat(aging.getBody().currencies()).isEmpty();

        assertThat(invoiceRepository.findById(invoice.id())).isPresent();
        assertThat(invoiceRepository.findById(invoice.id()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.paid);
    }

    @Test
    void overpaymentIsRejected() {
        InvoiceResponse invoice = createInvoice("Overpay Co", AS_OF, "usd", 1000);

        ResponseEntity<String> rejected = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/payments",
                new CreateInvoicePaymentRequest(1001, "usd"),
                String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ledgerEventRepository.count()).isZero();
    }

    private ResponseEntity<InvoicePaymentResponse> postPayment(String invoiceId, long amountMinor, String currency) {
        return restTemplate.postForEntity(
                "/api/invoices/" + invoiceId + "/payments",
                new CreateInvoicePaymentRequest(amountMinor, currency),
                InvoicePaymentResponse.class);
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
        Map<String, InvoiceAgingResponse.CurrencyAgingResponse> byCurrency = aging.currencies()
                .stream()
                .collect(Collectors.toMap(InvoiceAgingResponse.CurrencyAgingResponse::currency, Function.identity()));
        return byCurrency.get(currency);
    }
}
