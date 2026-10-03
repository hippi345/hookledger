package com.hookledger.api;

import com.hookledger.domain.InvoicePayment;
import java.time.Instant;

public record InvoicePaymentResponse(
        String id, String invoiceId, long amountMinor, String currency, String ledgerEventId, Instant createdAt) {

    public static InvoicePaymentResponse from(InvoicePayment payment) {
        return new InvoicePaymentResponse(
                payment.getPaymentId(),
                payment.getInvoice().getInvoiceId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getLedgerEventId(),
                payment.getCreatedAt());
    }
}
