package com.hookledger.api;

public record CreateCustomerPaymentRequest(long amountMinor, String currency) {}
