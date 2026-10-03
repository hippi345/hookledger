package com.hookledger.api;

import com.hookledger.service.InvoiceService;
import java.util.List;

public record InvoiceDetailResponse(
        InvoiceResponse invoice,
        long openBalanceMinor,
        List<InvoiceCreditNoteResponse> credits,
        List<InvoicePaymentResponse> payments) {

    public static InvoiceDetailResponse from(InvoiceService.InvoiceDetail detail) {
        return new InvoiceDetailResponse(
                InvoiceResponse.from(detail.invoice()),
                detail.openBalanceMinor(),
                detail.credits().stream().map(InvoiceCreditNoteResponse::from).toList(),
                detail.payments().stream().map(InvoicePaymentResponse::from).toList());
    }
}
