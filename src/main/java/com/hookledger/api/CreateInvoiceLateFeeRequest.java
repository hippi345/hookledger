package com.hookledger.api;

import java.time.LocalDate;

public record CreateInvoiceLateFeeRequest(long feeMinor, LocalDate asOf) {}
