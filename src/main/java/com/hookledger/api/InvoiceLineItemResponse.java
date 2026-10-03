package com.hookledger.api;

import com.hookledger.domain.InvoiceLineItem;

public record InvoiceLineItemResponse(String id, String description, long amountMinor) {

    public static InvoiceLineItemResponse from(InvoiceLineItem item) {
        return new InvoiceLineItemResponse(item.getLineItemId(), item.getDescription(), item.getAmountMinor());
    }
}
