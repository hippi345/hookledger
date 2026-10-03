package com.hookledger.api;

import com.hookledger.domain.InvoiceCreditNote;
import java.time.Instant;

public record InvoiceCreditNoteResponse(
        String id, String invoiceId, long amountMinor, String currency, Instant createdAt) {

    public static InvoiceCreditNoteResponse from(InvoiceCreditNote creditNote) {
        return new InvoiceCreditNoteResponse(
                creditNote.getCreditNoteId(),
                creditNote.getInvoice().getInvoiceId(),
                creditNote.getAmountMinor(),
                creditNote.getCurrency(),
                creditNote.getCreatedAt());
    }
}
