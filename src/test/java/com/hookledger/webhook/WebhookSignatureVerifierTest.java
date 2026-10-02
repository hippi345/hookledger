package com.hookledger.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    @Test
    void verifyAcceptsValidHexHmac() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("test-signing-key");
        byte[] body = "{\"id\":\"evt_1\",\"type\":\"charge\",\"amount\":100,\"currency\":\"usd\"}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = verifier.computeHexHmacSha256(body);
        assertThat(verifier.verify(body, signature)).isTrue();
    }

    @Test
    void verifyRejectsTamperedBody() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("test-signing-key");
        byte[] body = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = verifier.computeHexHmacSha256(body);
        byte[] tampered = "{\"id\":\"evt_2\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(tampered, signature)).isFalse();
    }
}
