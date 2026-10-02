package com.hookledger.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies HMAC-SHA256 signatures over the raw webhook body.
 *
 * @see com.hookledger.webhook.WebhookSignatureHeaders
 */
@Component
public class WebhookSignatureVerifier {

    private final byte[] secretBytes;

    public WebhookSignatureVerifier(@Value("${hookledger.webhook.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.secretBytes = new byte[0];
        } else {
            this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public boolean isConfigured() {
        return secretBytes.length > 0;
    }

    /**
     * Expected header value: lowercase hex digest (no prefix) of HMAC-SHA256(secret, rawBody).
     */
    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (!isConfigured() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expected = computeHexHmacSha256(rawBody);
        return constantTimeEquals(expected, signatureHeader.trim().toLowerCase());
    }

    public String computeHexHmacSha256(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody);
            return toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
