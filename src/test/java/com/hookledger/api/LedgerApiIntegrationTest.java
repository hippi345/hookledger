package com.hookledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hookledger.AbstractPostgresIntegrationTest;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.webhook.WebhookSignatureHeaders;
import com.hookledger.webhook.WebhookSignatureVerifier;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class LedgerApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @BeforeEach
    void cleanLedger() {
        ledgerEventRepository.deleteAll();
    }

    @Test
    void rejectsBadSignature() throws Exception {
        String payload = "{\"id\":\"evt_bad_sig\",\"type\":\"charge\",\"amount\":100,\"currency\":\"usd\"}";

        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureHeaders.SIGNATURE, "deadbeef")
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid signature"));

        assertThat(ledgerEventRepository.count()).isZero();
    }

    @Test
    void duplicateEventIdDoesNotCreateSecondRow() throws Exception {
        String payload = "{\"id\":\"evt_dup\",\"type\":\"charge\",\"amount\":500,\"currency\":\"USD\"}";
        postSignedWebhook(payload)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false));
        postSignedWebhook(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(ledgerEventRepository.count()).isOne();
    }

    @Test
    void replayUnknownEventIdReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/events/evt_missing/replay")).andExpect(status().isNotFound());
    }

    @Test
    void getEventById() throws Exception {
        String payload = "{\"id\":\"evt_get_one\",\"type\":\"charge\",\"amount\":42,\"currency\":\"eur\"}";
        postSignedWebhook(payload).andExpect(status().isCreated());

        mockMvc.perform(get("/api/events/evt_get_one"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("evt_get_one"))
                .andExpect(jsonPath("$.amountMinor").value(42))
                .andExpect(jsonPath("$.signed").value(true));

        mockMvc.perform(get("/api/events/evt_unknown")).andExpect(status().isNotFound());
    }

    @Test
    void balanceIsChargesMinusRefundsPayoutsExcluded() throws Exception {
        postSignedWebhook("{\"id\":\"evt_c\",\"type\":\"charge\",\"amount\":1000,\"currency\":\"usd\"}")
                .andExpect(status().isCreated());
        postSignedWebhook(
                        "{\"id\":\"evt_r\",\"type\":\"refund\",\"amount\":300,\"currency\":\"usd\",\"chargeId\":\"evt_c\"}")
                .andExpect(status().isCreated());
        postSignedWebhook(
                        "{\"id\":\"evt_p\",\"type\":\"payout\",\"amount\":9999,\"currency\":\"usd\",\"chargeIds\":[\"evt_c\"]}")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/balance/usd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balanceMinor").value(700));
    }

    @Test
    void regexRejectsInvalidCurrencyAndEventId() throws Exception {
        postSignedWebhook("{\"id\":\"evt_x\",\"type\":\"charge\",\"amount\":1,\"currency\":\"US\"}")
                .andExpect(status().isBadRequest());

        postSignedWebhook("{\"id\":\"bad id\",\"type\":\"charge\",\"amount\":1,\"currency\":\"usd\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void findEventByTimestampUsesBinarySearch() throws Exception {
        MvcResult created = postSignedWebhook(
                        "{\"id\":\"evt_ts\",\"type\":\"charge\",\"amount\":10,\"currency\":\"usd\"}")
                .andExpect(status().isCreated())
                .andReturn();
        String receivedAt = JsonPath.read(created.getResponse().getContentAsString(), "$.receivedAt");

        mockMvc.perform(get("/api/events/by-timestamp").param("at", receivedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("evt_ts"));
    }

    @Test
    void topChargesUsesPriorityQueueWithLimit() throws Exception {
        postSignedWebhook("{\"id\":\"c_small\",\"type\":\"charge\",\"amount\":100,\"currency\":\"usd\"}");
        postSignedWebhook("{\"id\":\"c_mid\",\"type\":\"charge\",\"amount\":500,\"currency\":\"usd\"}");
        postSignedWebhook("{\"id\":\"c_big\",\"type\":\"charge\",\"amount\":900,\"currency\":\"usd\"}");

        mockMvc.perform(get("/api/charges/top").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("c_big"))
                .andExpect(jsonPath("$[1].id").value("c_mid"));
    }

    @Test
    void replayUndoUsesStack() throws Exception {
        postSignedWebhook("{\"id\":\"evt_undo\",\"type\":\"charge\",\"amount\":1,\"currency\":\"usd\"}")
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/events/evt_undo/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayCount").value(1))
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(post("/api/events/replay/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayCount").value(0))
                .andExpect(jsonPath("$.replayed").value(false));
    }

    @Test
    void payoutGraphWalkReturnsSourceCharges() throws Exception {
        postSignedWebhook("{\"id\":\"g_c1\",\"type\":\"charge\",\"amount\":100,\"currency\":\"usd\"}");
        postSignedWebhook(
                "{\"id\":\"g_p1\",\"type\":\"payout\",\"amount\":100,\"currency\":\"usd\",\"chargeIds\":[\"g_c1\"]}");

        mockMvc.perform(get("/api/payouts/g_p1/charges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("g_c1"));
    }

    @Test
    void settlementGraphRejectsSelfReferencingRefund() throws Exception {
        postSignedWebhook(
                        "{\"id\":\"g_r1\",\"type\":\"refund\",\"amount\":10,\"currency\":\"usd\",\"chargeId\":\"g_r1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    private org.springframework.test.web.servlet.ResultActions postSignedWebhook(String payload)
            throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = verifier.computeHexHmacSha256(body);
        return mockMvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookSignatureHeaders.SIGNATURE, signature)
                .content(payload));
    }
}
