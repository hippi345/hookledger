package com.hookledger.api;

public record FxConversionResponse(
        String id,
        String rate,
        long sourceAmountMinor,
        String sourceCurrency,
        long convertedAmountMinor,
        String targetCurrency,
        EventResponse sourceLeg,
        EventResponse targetLeg) {}
