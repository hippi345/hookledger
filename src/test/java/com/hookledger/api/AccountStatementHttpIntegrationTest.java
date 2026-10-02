package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountStatementHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @BeforeEach
    void cleanLedger() {
        replayIdempotencyRepository.deleteAll();
        ledgerEventRepository.deleteAll();
    }

    @Test
    void cashStatementIncludesBalancesAndLines() {
        postWebhook("{\"id\":\"stmt_c\",\"type\":\"charge\",\"amount\":1500,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook(
                "{\"id\":\"stmt_r\",\"type\":\"refund\",\"amount\":500,\"currency\":\"usd\",\"chargeId\":\"stmt_c\"}",
                HttpStatus.CREATED);

        Instant from = Instant.parse("1970-01-01T00:00:00Z");
        Instant to = Instant.parse("2099-12-31T23:59:59Z");

        String url = UriComponentsBuilder.fromPath("/api/accounts/cash/statement")
                .queryParam("currency", "usd")
                .queryParam("from", from)
                .queryParam("to", to)
                .toUriString();

        ResponseEntity<AccountStatementResponse> response =
                restTemplate.getForEntity(url, AccountStatementResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountStatementResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.account()).isEqualTo("cash");
        assertThat(body.currency()).isEqualTo("USD");
        assertThat(body.startingBalanceMinor()).isZero();
        assertThat(body.endingBalanceMinor()).isEqualTo(1000);
        assertThat(body.lines()).hasSize(2);
        assertThat(body.lines().get(0).debitMinor()).isEqualTo(1500);
        assertThat(body.lines().get(1).creditMinor()).isEqualTo(500);
    }

    @Test
    void statementCsvMatchesJsonExport() {
        postWebhook("{\"id\":\"csv_c\",\"type\":\"charge\",\"amount\":200,\"currency\":\"eur\"}", HttpStatus.CREATED);

        Instant from = Instant.parse("1970-01-01T00:00:00Z");
        Instant to = Instant.parse("2099-12-31T23:59:59Z");
        String url = UriComponentsBuilder.fromPath("/api/accounts/cash/statement.csv")
                .queryParam("currency", "eur")
                .queryParam("from", from)
                .queryParam("to", to)
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("starting_balance_minor,ending_balance_minor");
        assertThat(response.getBody()).contains("csv_c,charge");
        assertThat(response.getBody()).contains(",200,0,EUR");
    }

    private ResponseEntity<String> postWebhook(String payload, HttpStatus expectedStatus) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WebhookSignatureHeaders.SIGNATURE, verifier.computeHexHmacSha256(body));

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/webhooks", new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        return response;
    }
}
