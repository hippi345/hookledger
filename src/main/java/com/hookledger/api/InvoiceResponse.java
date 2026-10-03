package com.hookledger.api;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(
        String id,
        String customerName,
        String customerAddress,
        LocalDate dueDate,
        String currency,
        InvoiceStatus status,
        long totalAmountMinor,
        Instant createdAt,
        Instant paidAt,
        String ledgerEventId,
        List<InvoiceLineItemResponse> lineItems) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getInvoiceId(),
                invoice.getCustomerName(),
                invoice.getCustomerAddress(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getTotalAmountMinor(),
                invoice.getCreatedAt(),
                invoice.getPaidAt(),
                invoice.getLedgerEventId(),
                invoice.getLineItems().stream().map(InvoiceLineItemResponse::from).toList());
    }
}
