package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.domain.LedgerAuditAction;
import com.hookledger.repository.LedgerAuditRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PeriodCloseRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.service.AuditActorContext;
import com.hookledger.service.LedgerAuditService;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditFeeFxHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private LedgerAuditRepository ledgerAuditRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @Autowired
    private PeriodCloseRepository periodCloseRepository;

    @BeforeEach
    void cleanLedger() {
        replayIdempotencyRepository.deleteAll();
        ledgerAuditRepository.deleteAll();
        ledgerEventRepository.deleteAll();
        periodCloseRepository.deleteAll();
    }

    @Test
    void auditTrailRecordsActorForPostReversalMatchAndPeriodClose() {
        HttpHeaders actor = actorHeaders("ops-analyst");

        postWebhook(
                "{\"id\":\"aud_c1\",\"type\":\"charge\",\"amount\":400,\"currency\":\"usd\"}",
                actor,
                HttpStatus.CREATED);

        ResponseEntity<java.util.List<AuditEntryResponse>> chargeAudit = restTemplate.exchange(
                "/api/events/aud_c1/audit",
                HttpMethod.GET,
                new HttpEntity<>(actor),
                new ParameterizedTypeReference<>() {});
        assertThat(chargeAudit.getBody()).hasSize(1);
        assertThat(chargeAudit.getBody().get(0).action()).isEqualTo(LedgerAuditAction.post);
        assertThat(chargeAudit.getBody().get(0).actor()).isEqualTo("ops-analyst");

        restTemplate.exchange(
                "/api/events/aud_c1/reverse",
                HttpMethod.POST,
                new HttpEntity<>(actorHeaders("reversal-bot")),
                EventResponse.class);
        ResponseEntity<java.util.List<AuditEntryResponse>> reversalAudit = restTemplate.exchange(
                "/api/events/aud_c1_reversal/audit",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(reversalAudit.getBody()).hasSize(1);
        assertThat(reversalAudit.getBody().get(0).action()).isEqualTo(LedgerAuditAction.reversal);

        postWebhook(
                "{\"id\":\"aud_c2\",\"type\":\"charge\",\"amount\":250,\"currency\":\"usd\"}",
                actor,
                HttpStatus.CREATED);
        ResponseEntity<BankLineResponse> bankLine = restTemplate.postForEntity(
                "/api/bank-lines",
                new PostBankLineRequest(250, "usd", java.time.LocalDate.parse("2024-01-15")),
                BankLineResponse.class);
        String bankLineId = bankLine.getBody().id();

        restTemplate.exchange(
                "/api/bank-lines/" + bankLineId + "/match",
                HttpMethod.POST,
                new HttpEntity<>(new MatchBankLineRequest("aud_c2"), actorHeaders("matcher")),
                BankLineResponse.class);

        ResponseEntity<java.util.List<AuditEntryResponse>> matchAudit = restTemplate.exchange(
                "/api/events/aud_c2/audit",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(matchAudit.getBody()).anyMatch(row -> row.action() == LedgerAuditAction.match);

        Instant lockAt = Instant.parse("2024-12-31T23:59:59Z");
        restTemplate.exchange(
                "/api/period-close?through=" + lockAt,
                HttpMethod.POST,
                new HttpEntity<>(actorHeaders("closer")),
                PeriodCloseResponse.class);

        ResponseEntity<java.util.List<AuditEntryResponse>> closeAudit = restTemplate.exchange(
                "/api/events/" + LedgerAuditService.PERIOD_CLOSE_EVENT_ID + "/audit",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(closeAudit.getBody()).isNotEmpty();
        assertThat(closeAudit.getBody().get(0).action()).isEqualTo(LedgerAuditAction.period_close);
    }

    @Test
    void chargeWithFeeCreatesSeparateBalancedFeeEvent() {
        postWebhook(
                "{\"id\":\"fee_c1\",\"type\":\"charge\",\"amount\":1000,\"currency\":\"usd\",\"feeMinor\":35}",
                actorHeaders("billing"),
                HttpStatus.CREATED);

        ResponseEntity<EventResponse> charge =
                restTemplate.getForEntity("/api/events/fee_c1", EventResponse.class);
        assertThat(charge.getBody()).isNotNull();
        assertThat(charge.getBody().amountMinor()).isEqualTo(1000);

        ResponseEntity<EventResponse> fee =
                restTemplate.getForEntity("/api/events/fee_c1_fee", EventResponse.class);
        assertThat(fee.getBody()).isNotNull();
        assertThat(fee.getBody().type().name()).isEqualTo("fee");
        assertThat(fee.getBody().amountMinor()).isEqualTo(35);
        assertThat(fee.getBody().debitMinor()).isEqualTo(35);
        assertThat(fee.getBody().creditMinor()).isEqualTo(-35);
        assertThat(fee.getBody().feeChargeId()).isEqualTo("fee_c1");

        ResponseEntity<TrialBalanceResponse> trial =
                restTemplate.getForEntity("/api/trial-balance", TrialBalanceResponse.class);
        assertThat(trial.getBody()).isNotNull();
        assertThat(trial.getBody().balanced()).isTrue();
    }

    @Test
    void fxConversionStoresRateAndBalancedLegs() {
        FxConversionRequest request =
                new FxConversionRequest("fx1", 10000, "usd", "eur", "0.92");
        ResponseEntity<FxConversionResponse> response = restTemplate.postForEntity(
                "/api/fx-conversions", request, FxConversionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rate()).isEqualTo("0.92");
        assertThat(response.getBody().convertedAmountMinor()).isEqualTo(9200);
        assertThat(response.getBody().sourceLeg().debitMinor() + response.getBody().sourceLeg().creditMinor())
                .isZero();
        assertThat(response.getBody().targetLeg().debitMinor() + response.getBody().targetLeg().creditMinor())
                .isZero();
        assertThat(response.getBody().sourceLeg().fxRate()).isEqualTo("0.92");
        assertThat(response.getBody().targetLeg().fxConvertedAmountMinor()).isEqualTo(9200);

        ResponseEntity<TrialBalanceResponse> trial =
                restTemplate.getForEntity("/api/trial-balance", TrialBalanceResponse.class);
        assertThat(trial.getBody()).isNotNull();
        assertThat(trial.getBody().balanced()).isTrue();
    }

    private void postWebhook(String payload, HttpHeaders extraHeaders, HttpStatus expectedStatus) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/webhooks",
                HttpMethod.POST,
                webhookRequest(payload, extraHeaders),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
    }

    private HttpEntity<String> webhookRequest(String payload, HttpHeaders extraHeaders) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WebhookSignatureHeaders.SIGNATURE, verifier.computeHexHmacSha256(body));
        if (extraHeaders != null) {
            headers.addAll(extraHeaders);
        }
        return new HttpEntity<>(payload, headers);
    }

    private static HttpHeaders actorHeaders(String actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuditActorContext.ACTOR_HEADER, actor);
        return headers;
    }
}
