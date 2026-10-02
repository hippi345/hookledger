package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PeriodCloseRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerFeaturesHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @Autowired
    private PeriodCloseRepository periodCloseRepository;

    @BeforeEach
    void cleanLedger() {
        replayIdempotencyRepository.deleteAll();
        ledgerEventRepository.deleteAll();
        periodCloseRepository.deleteAll();
    }

    @Test
    void reversalCreatesOppositeEntryAndKeepsTrialBalanceBalanced() {
        postWebhook("{\"id\":\"rev_charge\",\"type\":\"charge\",\"amount\":500,\"currency\":\"usd\"}", HttpStatus.CREATED);

        ResponseEntity<EventResponse> reversal = restTemplate.postForEntity(
                "/api/events/rev_charge/reverse", null, EventResponse.class);
        assertThat(reversal.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reversal.getBody()).isNotNull();
        assertThat(reversal.getBody().id()).isEqualTo("rev_charge_reversal");
        assertThat(reversal.getBody().debitMinor()).isEqualTo(-500);
        assertThat(reversal.getBody().creditMinor()).isEqualTo(500);
        assertThat(reversal.getBody().reversesEventId()).isEqualTo("rev_charge");

        ResponseEntity<EventResponse> original =
                restTemplate.getForEntity("/api/events/rev_charge", EventResponse.class);
        assertThat(original.getBody()).isNotNull();
        assertThat(original.getBody().reversed()).isTrue();

        ResponseEntity<TrialBalanceResponse> trial =
                restTemplate.getForEntity("/api/trial-balance", TrialBalanceResponse.class);
        assertThat(trial.getBody()).isNotNull();
        assertThat(trial.getBody().balanced()).isTrue();

        ResponseEntity<BalanceResponse> balance =
                restTemplate.getForEntity("/api/balance/usd", BalanceResponse.class);
        assertThat(balance.getBody()).isNotNull();
        assertThat(balance.getBody().balanceMinor()).isZero();
    }

    @Test
    void eventListFiltersByTypeAndCurrency() {
        postWebhook("{\"id\":\"f_c1\",\"type\":\"charge\",\"amount\":1,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook(
                "{\"id\":\"f_r1\",\"type\":\"refund\",\"amount\":2,\"currency\":\"eur\",\"chargeId\":\"f_c1\"}",
                HttpStatus.CREATED);
        postWebhook("{\"id\":\"f_c2\",\"type\":\"charge\",\"amount\":3,\"currency\":\"eur\"}", HttpStatus.CREATED);

        ResponseEntity<EventPageResponse> byType = restTemplate.exchange(
                "/api/events?type=charge&size=50",
                HttpMethod.GET,
                null,
                EventPageResponse.class);
        assertThat(byType.getBody()).isNotNull();
        assertThat(byType.getBody().totalElements()).isEqualTo(2);
        assertThat(byType.getBody().content()).extracting(EventResponse::id).containsExactlyInAnyOrder("f_c2", "f_c1");

        ResponseEntity<EventPageResponse> byCurrency = restTemplate.exchange(
                "/api/events?currency=EUR&size=50",
                HttpMethod.GET,
                null,
                EventPageResponse.class);
        assertThat(byCurrency.getBody()).isNotNull();
        assertThat(byCurrency.getBody().totalElements()).isEqualTo(2);
    }

    @Test
    void periodCloseRejectsBackdatedWebhook() {
        Instant postedAt = Instant.parse("2024-06-15T12:00:00Z");
        postWebhook(
                "{\"id\":\"pc_old\",\"type\":\"charge\",\"amount\":100,\"currency\":\"usd\",\"effectiveAt\":\""
                        + postedAt + "\"}",
                HttpStatus.CREATED);

        String closeUrl = UriComponentsBuilder.fromPath("/api/period-close")
                .queryParam("through", postedAt.toString())
                .toUriString();
        ResponseEntity<PeriodCloseResponse> close =
                restTemplate.postForEntity(closeUrl, null, PeriodCloseResponse.class);
        assertThat(close.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, String>> rejected = restTemplate.exchange(
                "/api/webhooks",
                HttpMethod.POST,
                webhookRequest(
                        "{\"id\":\"pc_new\",\"type\":\"charge\",\"amount\":50,\"currency\":\"usd\",\"effectiveAt\":\""
                                + postedAt + "\"}"),
                new ParameterizedTypeReference<>() {});
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody()).containsKey("error");
        assertThat(ledgerEventRepository.count()).isEqualTo(1);
    }

    private void postWebhook(String payload, HttpStatus expectedStatus) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/webhooks",
                HttpMethod.POST,
                webhookRequest(payload),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
    }

    private HttpEntity<String> webhookRequest(String payload) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WebhookSignatureHeaders.SIGNATURE, verifier.computeHexHmacSha256(body));
        return new HttpEntity<>(payload, headers);
    }
}
