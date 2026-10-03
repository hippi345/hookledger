package com.hookledger.api;

import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.domain.InvoiceSalesTax;

public record InvoiceLineItemResponse(
        String id, String description, long amountMinor, int taxRateBasisPoints, long taxMinor, long lineTotalMinor) {

    public static InvoiceLineItemResponse from(InvoiceLineItem item) {
        long taxMinor = InvoiceSalesTax.taxMinor(item.getAmountMinor(), item.getTaxRateBasisPoints());
        return new InvoiceLineItemResponse(
                item.getLineItemId(),
                item.getDescription(),
                item.getAmountMinor(),
                item.getTaxRateBasisPoints(),
                taxMinor,
                item.getAmountMinor() + taxMinor);
    }
}
