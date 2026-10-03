package com.hookledger.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.api.CreateInvoiceRequest;
import com.hookledger.api.InvoiceLineItemRequest;
import com.hookledger.api.InvoiceResponse;
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
        assertThat(js).contains("/pdf");
        assertThat(js).contains("statement.pdf");
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
