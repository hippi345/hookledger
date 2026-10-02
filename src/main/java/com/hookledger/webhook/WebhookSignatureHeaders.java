package com.hookledger.webhook;

public final class WebhookSignatureHeaders {

    /** HTTP header carrying the HMAC-SHA256 hex digest of the raw request body. */
    public static final String SIGNATURE = "X-Hookledger-Signature";

    private WebhookSignatureHeaders() {
    }
}
