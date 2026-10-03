package com.hookledger.api;

import com.hookledger.service.InvoiceService;
import java.time.Instant;

public record InvoicePaymentRefundResponse(
        String paymentId,
        String invoiceId,
        long amountMinor,
        String currency,
        String reversalLedgerEventId,
        Instant refundedAt) {

    public static InvoicePaymentRefundResponse from(InvoiceService.PaymentRefundResult result) {
        return new InvoicePaymentRefundResponse(
                result.payment().getPaymentId(),
                result.payment().getInvoice().getInvoiceId(),
                result.payment().getAmountMinor(),
                result.payment().getCurrency(),
                result.reversalLedgerEventId(),
                result.payment().getRefundedAt());
    }
}
