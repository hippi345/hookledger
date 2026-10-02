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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    private static final String PAYLOAD =
            "{\"id\":\"evt_integration_1\",\"type\":\"charge\",\"amount\":2500,\"currency\":\"EUR\"}";

    @BeforeEach
    void cleanLedger() {
        ledgerEventRepository.deleteAll();
    }

    @Test
    void rejectsUnsignedWebhook() throws Exception {
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsSignedWebhookAndPersistsOnce() throws Exception {
        byte[] body = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String signature = verifier.computeHexHmacSha256(body);

        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureHeaders.SIGNATURE, signature)
                        .content(PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("evt_integration_1"))
                .andExpect(jsonPath("$.type").value("charge"))
                .andExpect(jsonPath("$.amountMinor").value(2500))
                .andExpect(jsonPath("$.currency").value("EUR"));

        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureHeaders.SIGNATURE, signature)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("evt_integration_1"));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("evt_integration_1"));
    }

    @Test
    void replayIncrementsCounter() throws Exception {
        byte[] body = "{\"id\":\"evt_replay\",\"type\":\"payout\",\"amount\":99,\"currency\":\"GBP\"}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = verifier.computeHexHmacSha256(body);

        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureHeaders.SIGNATURE, signature)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/events/evt_replay/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayCount").value(1));
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiIsAvailable() throws Exception {
        mockMvc.perform(get("/openapi/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }
}
