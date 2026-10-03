package com.hookledger.api;

public record InvoiceLineItemRequest(String description, long amountMinor, Integer taxRateBasisPoints) {}
