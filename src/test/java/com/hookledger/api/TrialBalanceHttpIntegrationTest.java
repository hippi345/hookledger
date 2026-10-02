package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrialBalanceHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void emptyLedgerTrialBalanceIsBalanced() {
        TrialBalanceResponse body = getTrialBalance();
        assertThat(body.balanced()).isTrue();
        assertThat(body.netMinor()).isZero();
        assertThat(body.totalDebitMinor()).isZero();
        assertThat(body.totalCreditMinor()).isZero();
    }

    @Test
    void signedBalancedEventKeepsTrialBalanceAtZero() {
        postWebhook(
                "{\"id\":\"tb_charge\",\"type\":\"charge\",\"amount\":1500,\"currency\":\"usd\"}",
                HttpStatus.CREATED);

        TrialBalanceResponse balance = getTrialBalance();
        assertThat(balance.balanced()).isTrue();
        assertThat(balance.netMinor()).isZero();
        assertThat(balance.totalDebitMinor()).isEqualTo(balance.totalCreditMinor());
        assertThat(balance.totalDebitMinor()).isEqualTo(1500);
        assertThat(balance.lines()).anyMatch(
                line -> line.account().equals("cash") && line.debitMinor() == 1500 && line.creditMinor() == 0);
        assertThat(balance.lines()).anyMatch(
                line -> line.account().equals("revenue") && line.creditMinor() == 1500 && line.debitMinor() == 0);
    }

    @Test
    void rejectsUnbalancedEventAndLeavesTrialBalanceZero() {
        ResponseEntity<String> response = postWebhook(
                "{\"id\":\"tb_bad\",\"type\":\"charge\",\"amount\":100,\"currency\":\"usd\",\"debitMinor\":100,\"creditMinor\":-50}",
                HttpStatus.BAD_REQUEST);

        assertThat(response.getBody()).contains("Ledger sides must sum to zero");
        assertThat(ledgerEventRepository.count()).isZero();

        TrialBalanceResponse balance = getTrialBalance();
        assertThat(balance.balanced()).isTrue();
        assertThat(balance.netMinor()).isZero();
        assertThat(balance.totalDebitMinor()).isZero();
    }

    private TrialBalanceResponse getTrialBalance() {
        ResponseEntity<TrialBalanceResponse> response =
                restTemplate.getForEntity("/api/trial-balance", TrialBalanceResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
