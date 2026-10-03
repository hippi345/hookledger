package com.hookledger.api;

public record CreateInvoicePaymentRequest(long amountMinor, String currency) {}
