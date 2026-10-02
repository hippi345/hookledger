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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentReplayHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void replayWithSameIdempotencyKeyAppliesOnce() {
        postWebhook("{\"id\":\"idem_evt\",\"type\":\"charge\",\"amount\":10,\"currency\":\"usd\"}", HttpStatus.CREATED);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "replay-key-1");

        ResponseEntity<EventResponse> first = restTemplate.exchange(
                "/api/events/idem_evt/replay", HttpMethod.POST, new HttpEntity<>(headers), EventResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().replayCount()).isEqualTo(1);

        ResponseEntity<EventResponse> second = restTemplate.exchange(
                "/api/events/idem_evt/replay", HttpMethod.POST, new HttpEntity<>(headers), EventResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().replayCount()).isEqualTo(1);

        assertThat(replayIdempotencyRepository.count()).isOne();
    }

    private void postWebhook(String payload, HttpStatus expectedStatus) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WebhookSignatureHeaders.SIGNATURE, verifier.computeHexHmacSha256(body));

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/webhooks", new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
    }
}
