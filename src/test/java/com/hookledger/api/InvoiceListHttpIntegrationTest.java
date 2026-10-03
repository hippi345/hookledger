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
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceListHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void listFiltersByCustomerStatusAndCurrencyWithPagination() {
        InvoiceResponse openUsd = createInvoice("Filter Co", LocalDate.parse("2026-05-01"), "usd", 1000);
        InvoiceResponse openEur = createInvoice("Filter Co", LocalDate.parse("2026-05-02"), "eur", 2000);
        InvoiceResponse other = createInvoice("Other Co", LocalDate.parse("2026-05-03"), "usd", 3000);
        InvoiceResponse toVoid = createInvoice("Filter Co", LocalDate.parse("2026-05-04"), "usd", 4000);

        assertThat(restTemplate
                        .postForEntity("/api/invoices/" + openUsd.id() + "/pay", null, InvoiceResponse.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate
                        .postForEntity("/api/invoices/" + toVoid.id() + "/void", null, InvoiceResponse.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<InvoiceListResponse> openFilterCo = restTemplate.getForEntity(
                listUrl()
                        .queryParam("customer", "Filter Co")
                        .queryParam("status", "open")
                        .queryParam("limit", 10)
                        .queryParam("offset", 0)
                        .build()
                        .toUri(),
                InvoiceListResponse.class);
        assertThat(openFilterCo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openFilterCo.getBody()).isNotNull();
        assertThat(openFilterCo.getBody().total()).isEqualTo(1);
        assertThat(openFilterCo.getBody().invoices()).hasSize(1);
        assertThat(openFilterCo.getBody().invoices().get(0).id()).isEqualTo(openEur.id());
        assertThat(openFilterCo.getBody().invoices().get(0).status()).isEqualTo(InvoiceStatus.open);

        ResponseEntity<InvoiceListResponse> usdOnly = restTemplate.getForEntity(
                listUrl().queryParam("currency", "usd").queryParam("limit", 1).queryParam("offset", 0).build().toUri(),
                InvoiceListResponse.class);
        assertThat(usdOnly.getBody()).isNotNull();
        assertThat(usdOnly.getBody().total()).isEqualTo(3);
        assertThat(usdOnly.getBody().invoices()).hasSize(1);

        ResponseEntity<InvoiceListResponse> voidOnly = restTemplate.getForEntity(
                listUrl().queryParam("status", "void").build().toUri(), InvoiceListResponse.class);
        assertThat(voidOnly.getBody()).isNotNull();
        assertThat(voidOnly.getBody().invoices()).hasSize(1);
        assertThat(voidOnly.getBody().invoices().get(0).id()).isEqualTo(toVoid.id());
        assertThat(voidOnly.getBody().invoices().get(0).status()).isEqualTo(InvoiceStatus.voided);

        ResponseEntity<InvoiceListResponse> paidOnly = restTemplate.getForEntity(
                listUrl().queryParam("customer", "Filter Co").queryParam("status", "paid").build().toUri(),
                InvoiceListResponse.class);
        assertThat(paidOnly.getBody()).isNotNull();
        assertThat(paidOnly.getBody().invoices()).hasSize(1);
        assertThat(paidOnly.getBody().invoices().get(0).id()).isEqualTo(openUsd.id());

        ResponseEntity<InvoiceListResponse> otherCustomer = restTemplate.getForEntity(
                listUrl().queryParam("customer", "Other Co").build().toUri(), InvoiceListResponse.class);
        assertThat(otherCustomer.getBody()).isNotNull();
        assertThat(otherCustomer.getBody().invoices()).hasSize(1);
        assertThat(otherCustomer.getBody().invoices().get(0).id()).isEqualTo(other.id());
    }

    private static UriComponentsBuilder listUrl() {
        return UriComponentsBuilder.fromPath("/api/invoices");
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
