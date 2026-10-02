package com.hookledger.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hookledger.domain.MoneyEventType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MoneyEventPayload(
        String id,
        MoneyEventType type,
        long amount,
        String currency) {
}
