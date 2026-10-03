package com.hookledger.api;

import java.time.LocalDate;

public record PostBankLineRequest(long amountMinor, String currency, LocalDate date) {}
