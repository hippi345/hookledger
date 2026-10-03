package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.BankLineCombinationChargeRepository;
import com.hookledger.repository.BankLineRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PayoutSplitChargeRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
class MatchingAlgorithmsHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private ReplayIdempotencyRepository replayIdempotencyRepository;

    @Autowired
    private BankLineRepository bankLineRepository;

    @Autowired
    private BankLineCombinationChargeRepository combinationChargeRepository;

    @Autowired
    private PayoutSplitChargeRepository payoutSplitChargeRepository;

    @BeforeEach
    void cleanData() {
        combinationChargeRepository.deleteAll();
        payoutSplitChargeRepository.deleteAll();
        bankLineRepository.deleteAll();
        replayIdempotencyRepository.deleteAll();
        ledgerEventRepository.deleteAll();
    }

    @Test
    void greedyMatchLinksOldestLedgerEntry() {
        postWebhook("{\"id\":\"g_old\",\"type\":\"charge\",\"amount\":1200,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook("{\"id\":\"g_new\",\"type\":\"charge\",\"amount\":1200,\"currency\":\"usd\"}", HttpStatus.CREATED);

        ResponseEntity<BankLineResponse> posted = restTemplate.postForEntity(
                "/api/bank-lines",
                new PostBankLineRequest(1200, "usd", LocalDate.parse("2024-05-01")),
                BankLineResponse.class);
        String bankLineId = posted.getBody().id();

        ResponseEntity<BankLineMatchSuggestionResponse> suggestion = restTemplate.getForEntity(
                "/api/bank-lines/" + bankLineId + "/match-suggestion", BankLineMatchSuggestionResponse.class);
        assertThat(suggestion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(suggestion.getBody().ledgerEventId()).isEqualTo("g_old");

        ResponseEntity<BankLineResponse> matched = restTemplate.postForEntity(
                "/api/bank-lines/" + bankLineId + "/match-greedy", null, BankLineResponse.class);
        assertThat(matched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(matched.getBody().matchedLedgerEventId()).isEqualTo("g_old");
        assertThat(ledgerEventRepository.count()).isEqualTo(2);
        assertThat(bankLineRepository.count()).isEqualTo(1);
    }

    @Test
    void combinationMatchCoversBankLineWithoutDeletingCharges() {
        postWebhook("{\"id\":\"c_a\",\"type\":\"charge\",\"amount\":300,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook("{\"id\":\"c_b\",\"type\":\"charge\",\"amount\":500,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook("{\"id\":\"c_c\",\"type\":\"charge\",\"amount\":200,\"currency\":\"usd\"}", HttpStatus.CREATED);

        ResponseEntity<BankLineResponse> posted = restTemplate.postForEntity(
                "/api/bank-lines",
                new PostBankLineRequest(1000, "usd", LocalDate.parse("2024-06-01")),
                BankLineResponse.class);
        String bankLineId = posted.getBody().id();

        ResponseEntity<BankLineCombinationMatchResponse> matched = restTemplate.postForEntity(
                "/api/bank-lines/" + bankLineId + "/match-combination", null, BankLineCombinationMatchResponse.class);
        assertThat(matched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(matched.getBody().matchedChargeIds()).containsExactlyInAnyOrder("c_a", "c_b", "c_c");
        assertThat(ledgerEventRepository.count()).isEqualTo(3);
        assertThat(combinationChargeRepository.count()).isEqualTo(3);
    }

    @Test
    void payoutSplitUsesFewestCharges() {
        postWebhook("{\"id\":\"p_c1\",\"type\":\"charge\",\"amount\":400,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook("{\"id\":\"p_c2\",\"type\":\"charge\",\"amount\":600,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook("{\"id\":\"p_c3\",\"type\":\"charge\",\"amount\":1000,\"currency\":\"usd\"}", HttpStatus.CREATED);
        postWebhook(
                "{\"id\":\"p_out\",\"type\":\"payout\",\"amount\":1000,\"currency\":\"usd\",\"chargeIds\":[\"p_c1\",\"p_c2\"]}",
                HttpStatus.CREATED);

        ResponseEntity<PayoutSplitResponse> split = restTemplate.postForEntity(
                "/api/payouts/p_out/split", null, PayoutSplitResponse.class);
        assertThat(split.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(split.getBody().chargeIds()).containsExactly("p_c3");
        assertThat(payoutSplitChargeRepository.count()).isEqualTo(1);
        assertThat(ledgerEventRepository.count()).isEqualTo(4);
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
