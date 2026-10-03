package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.BankLineRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BankReconciliationHttpIntegrationTest extends AbstractPostgresIntegrationTest {

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

    @BeforeEach
    void cleanData() {
        bankLineRepository.deleteAll();
        replayIdempotencyRepository.deleteAll();
        ledgerEventRepository.deleteAll();
    }

    @Test
    void matchRemovesBankLineFromUnmatchedList() {
        postWebhook("{\"id\":\"br_c1\",\"type\":\"charge\",\"amount\":2500,\"currency\":\"usd\"}", HttpStatus.CREATED);

        PostBankLineRequest postRequest = new PostBankLineRequest(2500, "usd", LocalDate.parse("2024-03-01"));
        ResponseEntity<BankLineResponse> posted = restTemplate.postForEntity(
                "/api/bank-lines", postRequest, BankLineResponse.class);
        assertThat(posted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(posted.getBody()).isNotNull();
        String bankLineId = posted.getBody().id();

        ResponseEntity<BankLineResponse[]> unmatchedBefore =
                restTemplate.getForEntity("/api/bank-lines/unmatched", BankLineResponse[].class);
        assertThat(unmatchedBefore.getBody()).extracting(BankLineResponse::id).containsExactly(bankLineId);

        MatchBankLineRequest matchRequest = new MatchBankLineRequest("br_c1");
        ResponseEntity<BankLineResponse> matched = restTemplate.postForEntity(
                "/api/bank-lines/" + bankLineId + "/match", matchRequest, BankLineResponse.class);
        assertThat(matched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(matched.getBody()).isNotNull();
        assertThat(matched.getBody().matchedLedgerEventId()).isEqualTo("br_c1");

        ResponseEntity<BankLineResponse[]> unmatchedAfter =
                restTemplate.getForEntity("/api/bank-lines/unmatched", BankLineResponse[].class);
        assertThat(unmatchedAfter.getBody()).isEmpty();
        assertThat(bankLineRepository.count()).isEqualTo(1);
        assertThat(ledgerEventRepository.count()).isEqualTo(1);
    }

    @Test
    void matchRejectsAmountOrCurrencyMismatch() {
        postWebhook("{\"id\":\"br_c2\",\"type\":\"charge\",\"amount\":900,\"currency\":\"usd\"}", HttpStatus.CREATED);

        ResponseEntity<BankLineResponse> wrongAmount = restTemplate.postForEntity(
                "/api/bank-lines",
                new PostBankLineRequest(901, "usd", LocalDate.parse("2024-04-01")),
                BankLineResponse.class);
        String wrongAmountId = wrongAmount.getBody().id();

        ResponseEntity<Map<String, String>> amountRejected = restTemplate.exchange(
                "/api/bank-lines/" + wrongAmountId + "/match",
                HttpMethod.POST,
                new HttpEntity<>(new MatchBankLineRequest("br_c2")),
                new ParameterizedTypeReference<>() {});
        assertThat(amountRejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(amountRejected.getBody()).containsKey("error");

        ResponseEntity<BankLineResponse> wrongCurrency = restTemplate.postForEntity(
                "/api/bank-lines",
                new PostBankLineRequest(900, "eur", LocalDate.parse("2024-04-02")),
                BankLineResponse.class);
        String wrongCurrencyId = wrongCurrency.getBody().id();

        ResponseEntity<Map<String, String>> currencyRejected = restTemplate.exchange(
                "/api/bank-lines/" + wrongCurrencyId + "/match",
                HttpMethod.POST,
                new HttpEntity<>(new MatchBankLineRequest("br_c2")),
                new ParameterizedTypeReference<>() {});
        assertThat(currencyRejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(currencyRejected.getBody()).containsKey("error");

        ResponseEntity<BankLineResponse[]> unmatched =
                restTemplate.getForEntity("/api/bank-lines/unmatched", BankLineResponse[].class);
        assertThat(unmatched.getBody()).hasSize(2);
        assertThat(Arrays.stream(unmatched.getBody()).map(BankLineResponse::id))
                .containsExactlyInAnyOrder(wrongAmountId, wrongCurrencyId);
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
