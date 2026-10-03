package com.hookledger.api;

import com.hookledger.domain.InvoiceLateFee;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceLateFeeResponse(
        String id,
        String invoiceId,
        long feeMinor,
        String currency,
        LocalDate asOf,
        String ledgerEventId,
        Instant createdAt) {

    public static InvoiceLateFeeResponse from(InvoiceLateFee lateFee) {
        return new InvoiceLateFeeResponse(
                lateFee.getLateFeeId(),
                lateFee.getInvoice().getInvoiceId(),
                lateFee.getFeeMinor(),
                lateFee.getCurrency(),
                lateFee.getAsOf(),
                lateFee.getLedgerEventId(),
                lateFee.getCreatedAt());
    }
}
