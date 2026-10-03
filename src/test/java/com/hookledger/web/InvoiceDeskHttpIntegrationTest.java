package com.hookledger.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceDeskHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
}
