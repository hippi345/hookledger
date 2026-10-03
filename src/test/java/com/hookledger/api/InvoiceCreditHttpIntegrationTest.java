package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.domain.InvoiceStatus;
import com.hookledger.repository.InvoiceCreditNoteRepository;
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
class InvoiceCreditHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @Autowired
    private PeriodCloseRepository periodCloseRepository;

    @BeforeEach
    void cleanData() {
        replayIdempotencyRepository.deleteAll();
        invoiceCreditNoteRepository.deleteAll();
        invoiceRepository.deleteAll();
        ledgerEventRepository.deleteAll();
        periodCloseRepository.deleteAll();
    }

    @Test
    void partialCreditReducesAgingOpenAmount() {
        InvoiceResponse invoice = createInvoice("Credit Test Co", AS_OF.minusDays(10), "usd", 5000);

        ResponseEntity<InvoiceCreditNoteResponse> credit = restTemplate.postForEntity(
                "/api/invoices/" + invoice.id() + "/credits",
                new CreateInvoiceCreditRequest(2000, "usd"),
                InvoiceCreditNoteResponse.class);
        assertThat(credit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ledgerEventRepository.count()).isZero();

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
    void fullCreditRemovesInvoiceFromAging() {
        InvoiceResponse invoice = createInvoice("Fully Credited Co", AS_OF.minusDays(5), "usd", 4000);

        assertThat(postCredit(invoice.id(), 1500, "usd").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postCredit(invoice.id(), 2500, "usd").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ledgerEventRepository.count()).isZero();

        ResponseEntity<InvoiceAgingResponse> aging =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(aging.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aging.getBody()).isNotNull();
        assertThat(aging.getBody().currencies()).isEmpty();
    }

    @Test
    void payPartiallyCreditedInvoiceChargesOpenBalanceOnly() {
        InvoiceResponse invoice = createInvoice("Pay After Credit Co", LocalDate.parse("2026-04-15"), "usd", 3250);

        assertThat(postCredit(invoice.id(), 750, "usd").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ledgerEventRepository.count()).isZero();

        ResponseEntity<InvoiceResponse> paid =
                restTemplate.postForEntity("/api/invoices/" + invoice.id() + "/pay", null, InvoiceResponse.class);
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paid.getBody()).isNotNull();
        assertThat(paid.getBody().status()).isEqualTo(InvoiceStatus.paid);
        assertThat(ledgerEventRepository.count()).isEqualTo(1);

        var charge = ledgerEventRepository.findById(paid.getBody().ledgerEventId()).orElseThrow();
        assertThat(charge.getAmountMinor()).isEqualTo(2500);
        assertThat(charge.getDebitMinor()).isEqualTo(2500);
        assertThat(charge.getCreditMinor()).isEqualTo(-2500);
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

    private static InvoiceAgingResponse.CurrencyAgingResponse currencyAging(
            InvoiceAgingResponse aging, String currency) {
        Map<String, InvoiceAgingResponse.CurrencyAgingResponse> byCurrency = aging.currencies()
                .stream()
                .collect(Collectors.toMap(InvoiceAgingResponse.CurrencyAgingResponse::currency, Function.identity()));
        return byCurrency.get(currency);
    }
}
