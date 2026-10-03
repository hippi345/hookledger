package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
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
class InvoiceAgingHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void agingBucketsUnpaidInvoicesByDaysPastDue() {
        InvoiceResponse current = createInvoice("Current Co", AS_OF, "usd", 1000);
        InvoiceResponse days1To30 = createInvoice("Late 30 Co", AS_OF.minusDays(30), "usd", 2000);
        InvoiceResponse days31To60 = createInvoice("Late 60 Co", AS_OF.minusDays(31), "usd", 3000);
        InvoiceResponse days61To90 = createInvoice("Late 90 Co", AS_OF.minusDays(61), "usd", 4000);
        InvoiceResponse over90 = createInvoice("Very Late Co", AS_OF.minusDays(91), "usd", 5000);
        InvoiceResponse paid = createInvoice("Paid Co", AS_OF.minusDays(10), "usd", 6000);
        InvoiceResponse eurOpen = createInvoice("Euro Co", AS_OF.minusDays(5), "eur", 700);

        ResponseEntity<InvoiceResponse> paidResponse =
                restTemplate.postForEntity("/api/invoices/" + paid.id() + "/pay", null, InvoiceResponse.class);
        assertThat(paidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<InvoiceAgingResponse> aging =
                restTemplate.getForEntity("/api/invoices/aging?asOf=" + AS_OF, InvoiceAgingResponse.class);
        assertThat(aging.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aging.getBody()).isNotNull();
        assertThat(aging.getBody().asOf()).isEqualTo(AS_OF);
        assertThat(ledgerEventRepository.count()).isEqualTo(1);

        Map<String, InvoiceAgingResponse.CurrencyAgingResponse> byCurrency = aging.getBody()
                .currencies()
                .stream()
                .collect(Collectors.toMap(InvoiceAgingResponse.CurrencyAgingResponse::currency, Function.identity()));

        InvoiceAgingResponse.CurrencyAgingResponse usd = byCurrency.get("USD");
        assertThat(usd).isNotNull();
        assertThat(usd.grandTotalAmountMinor()).isEqualTo(15000);
        assertThat(usd.current().totalAmountMinor()).isEqualTo(1000);
        assertThat(usd.days1To30().totalAmountMinor()).isEqualTo(2000);
        assertThat(usd.days31To60().totalAmountMinor()).isEqualTo(3000);
        assertThat(usd.days61To90().totalAmountMinor()).isEqualTo(4000);
        assertThat(usd.over90().totalAmountMinor()).isEqualTo(5000);

        assertInvoiceIds(usd.current(), current.id());
        assertInvoiceIds(usd.days1To30(), days1To30.id());
        assertInvoiceIds(usd.days31To60(), days31To60.id());
        assertInvoiceIds(usd.days61To90(), days61To90.id());
        assertInvoiceIds(usd.over90(), over90.id());
        assertThat(allInvoiceIds(usd)).doesNotContain(paid.id());

        InvoiceAgingResponse.CurrencyAgingResponse eur = byCurrency.get("EUR");
        assertThat(eur).isNotNull();
        assertThat(eur.grandTotalAmountMinor()).isEqualTo(700);
        assertThat(eur.days1To30().totalAmountMinor()).isEqualTo(700);
        assertInvoiceIds(eur.days1To30(), eurOpen.id());
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

    private static void assertInvoiceIds(InvoiceAgingResponse.BucketResponse bucket, String... expectedIds) {
        assertThat(bucket.invoices().stream().map(InvoiceAgingResponse.InvoiceAgingLineResponse::id).toList())
                .containsExactly(expectedIds);
    }

    private static List<String> allInvoiceIds(InvoiceAgingResponse.CurrencyAgingResponse currency) {
        return List.of(
                        currency.current(),
                        currency.days1To30(),
                        currency.days31To60(),
                        currency.days61To90(),
                        currency.over90())
                .stream()
                .flatMap(bucket -> bucket.invoices().stream())
                .map(InvoiceAgingResponse.InvoiceAgingLineResponse::id)
                .toList();
    }
}
