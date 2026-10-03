package com.hookledger.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.api.CreateCustomerPaymentRequest;
import com.hookledger.api.CreateInvoicePaymentRequest;
import com.hookledger.api.CreateInvoiceRequest;
import com.hookledger.api.CreateInvoiceScheduleRequest;
import com.hookledger.api.CustomerPaymentResponse;
import com.hookledger.api.InvoiceLineItemRequest;
import com.hookledger.api.InvoiceOverdueResponse;
import com.hookledger.api.InvoicePaymentRefundResponse;
import com.hookledger.api.InvoicePaymentResponse;
import com.hookledger.api.InvoiceResponse;
import com.hookledger.api.InvoiceScheduleResponse;
import com.hookledger.domain.InvoiceScheduleInterval;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceDeskHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Pattern BUNDLE_SCRIPT =
            Pattern.compile("<script[^>]+src=\"(/invoice-desk/assets/[^\"]+\\.js)\"");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void invoiceDeskPageLoadsFromStaticResources() {
        ResponseEntity<String> page =
                restTemplate.getForEntity("/invoice-desk/index.html", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).isNotNull();
        assertThat(page.getBody()).contains("data-hookledger-page=\"invoice-desk\"");
        assertThat(page.getBody()).contains("<title>Invoice Desk</title>");
    }

    @Test
    void invoiceDeskRootForwardsToIndexHtml() {
        ResponseEntity<String> page = restTemplate.getForEntity("/invoice-desk/", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).isNotNull();
        assertThat(page.getBody()).contains("<title>Invoice Desk</title>");
    }

    @Test
    void bundledInvoiceDeskReferencesWriteApiPaths() {
        String indexHtml = restTemplate.getForObject("/invoice-desk/index.html", String.class);
        assertThat(indexHtml).isNotNull();
        Matcher matcher = BUNDLE_SCRIPT.matcher(indexHtml);
        assertThat(matcher.find()).isTrue();
        String bundlePath = matcher.group(1);
        ResponseEntity<String> bundle = restTemplate.getForEntity(bundlePath, String.class);
        assertThat(bundle.getStatusCode()).isEqualTo(HttpStatus.OK);
        String js = bundle.getBody();
        assertThat(js).isNotNull();
        assertThat(js).contains("/api/invoices");
        assertThat(js).contains("/credits");
        assertThat(js).contains("/payments");
        assertThat(js).contains("/pay");
        assertThat(js).contains("/void");
        assertThat(js).contains("/write-off");
        assertThat(js).contains("/late-fee");
        assertThat(js).contains("/refund");
        assertThat(js).contains("/api/invoices/overdue");
        assertThat(js).contains("/api/invoice-schedules/");
        assertThat(js).contains("/generate");
        assertThat(js).contains("/api/customers/");
        assertThat(js).contains("/pdf");
        assertThat(js).contains("statement.pdf");
    }

    @Test
    void invoiceDeskExtendedWriteFlowsMatchApiUsedByPage() {
        LocalDate overdueDue = LocalDate.parse("2026-03-01");
        LocalDate asOf = LocalDate.parse("2026-06-15");
        CreateInvoiceRequest overdueRequest = new CreateInvoiceRequest(
                "Desk Overdue Fable Co",
                null,
                overdueDue,
                "usd",
                List.of(new InvoiceLineItemRequest("Overdue line", 4000, null)));
        ResponseEntity<InvoiceResponse> overdueInvoice =
                restTemplate.postForEntity("/api/invoices", overdueRequest, InvoiceResponse.class);
        assertThat(overdueInvoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<InvoiceOverdueResponse> overdueList = restTemplate.getForEntity(
                "/api/invoices/overdue?asOf=" + asOf, InvoiceOverdueResponse.class);
        assertThat(overdueList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overdueList.getBody()).isNotNull();
        assertThat(overdueList.getBody().customers()).isNotEmpty();

        CreateInvoiceScheduleRequest scheduleRequest = new CreateInvoiceScheduleRequest(
                "Desk Schedule Fable Co",
                null,
                "usd",
                InvoiceScheduleInterval.monthly,
                List.of(new InvoiceLineItemRequest("Subscription", 2500, null)));
        ResponseEntity<InvoiceScheduleResponse> scheduleCreated = restTemplate.postForEntity(
                "/api/invoice-schedules", scheduleRequest, InvoiceScheduleResponse.class);
        assertThat(scheduleCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String scheduleId = scheduleCreated.getBody().id();

        ResponseEntity<InvoiceResponse> generated = restTemplate.postForEntity(
                "/api/invoice-schedules/" + scheduleId + "/generate", null, InvoiceResponse.class);
        assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(generated.getBody()).isNotNull();

        CreateInvoiceRequest payRequest = new CreateInvoiceRequest(
                "Desk Allocate Fable Co",
                null,
                LocalDate.parse("2026-04-01"),
                "usd",
                List.of(new InvoiceLineItemRequest("Allocate line", 2000, null)));
        ResponseEntity<InvoiceResponse> allocateInvoice =
                restTemplate.postForEntity("/api/invoices", payRequest, InvoiceResponse.class);
        assertThat(allocateInvoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<CustomerPaymentResponse> customerPayment = restTemplate.postForEntity(
                "/api/customers/Desk Allocate Fable Co/payments",
                new CreateCustomerPaymentRequest(1500, "usd"),
                CustomerPaymentResponse.class);
        assertThat(customerPayment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(customerPayment.getBody()).isNotNull();
        assertThat(customerPayment.getBody().appliedAmountMinor()).isEqualTo(1500);

        CreateInvoiceRequest refundRequest = new CreateInvoiceRequest(
                "Desk Refund Fable Co",
                null,
                LocalDate.parse("2026-05-01"),
                "usd",
                List.of(new InvoiceLineItemRequest("Refund line", 5000, null)));
        ResponseEntity<InvoiceResponse> refundInvoice =
                restTemplate.postForEntity("/api/invoices", refundRequest, InvoiceResponse.class);
        assertThat(refundInvoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String refundInvoiceId = refundInvoice.getBody().id();

        ResponseEntity<InvoicePaymentResponse> payment = restTemplate.postForEntity(
                "/api/invoices/" + refundInvoiceId + "/payments",
                new CreateInvoicePaymentRequest(2000, "usd"),
                InvoicePaymentResponse.class);
        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<InvoicePaymentRefundResponse> refunded = restTemplate.postForEntity(
                "/api/invoices/" + refundInvoiceId + "/payments/" + payment.getBody().id() + "/refund",
                null,
                InvoicePaymentRefundResponse.class);
        assertThat(refunded.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> detail =
                restTemplate.getForEntity("/api/invoices/" + refundInvoiceId, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).contains("\"openBalanceMinor\":5000");
    }

    @Test
    void invoiceDeskPdfPreviewEndpointsReturnPdfBytes() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "Screenshot Sample Co",
                null,
                LocalDate.parse("2026-05-01"),
                "usd",
                List.of(new InvoiceLineItemRequest("Desk preview line", 1200, null)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        String invoiceId = created.getBody().id();

        ResponseEntity<byte[]> invoicePdf =
                restTemplate.getForEntity("/api/invoices/" + invoiceId + "/pdf", byte[].class);
        assertThat(invoicePdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invoicePdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertPdfBytes(invoicePdf.getBody());

        String statementUrl = UriComponentsBuilder.fromPath("/api/customers/{customer}/statement.pdf")
                .queryParam("currency", "usd")
                .queryParam("from", "2026-01-01")
                .queryParam("to", "2026-12-31")
                .buildAndExpand("Screenshot Sample Co")
                .toUriString();
        ResponseEntity<byte[]> statementPdf = restTemplate.getForEntity(statementUrl, byte[].class);
        assertThat(statementPdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statementPdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertPdfBytes(statementPdf.getBody());
    }

    @Test
    void invoiceDeskWriteFlowMatchesApiUsedByPage() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "Desk Write Test LLC",
                null,
                LocalDate.parse("2026-04-01"),
                "usd",
                List.of(new InvoiceLineItemRequest("Integration line", 3000, null)));
        ResponseEntity<InvoiceResponse> created =
                restTemplate.postForEntity("/api/invoices", request, InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = created.getBody().id();

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(
                "/api/invoices/" + invoiceId + "/credits",
                HttpMethod.POST,
                new HttpEntity<>("{\"amountMinor\":500,\"currency\":\"usd\"}", jsonHeaders),
                String.class);
        restTemplate.exchange(
                "/api/invoices/" + invoiceId + "/payments",
                HttpMethod.POST,
                new HttpEntity<>("{\"amountMinor\":1000,\"currency\":\"usd\"}", jsonHeaders),
                String.class);

        ResponseEntity<String> detail =
                restTemplate.getForEntity("/api/invoices/" + invoiceId, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).contains("\"openBalanceMinor\":1500");
    }

    private static void assertPdfBytes(byte[] body) {
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(100);
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }
}
