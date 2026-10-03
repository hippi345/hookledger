package com.hookledger.api;

public record FxConversionRequest(
        String id,
        long sourceAmountMinor,
        String sourceCurrency,
        String targetCurrency,
        String rate) {}
