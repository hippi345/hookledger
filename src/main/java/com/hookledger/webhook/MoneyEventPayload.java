package com.hookledger.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hookledger.domain.MoneyEventType;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MoneyEventPayload(
        String id,
        MoneyEventType type,
        long amount,
        String currency,
        String chargeId,
        List<String> chargeIds,
        Long debitMinor,
        Long creditMinor) {
}
