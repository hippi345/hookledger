package com.hookledger.api;

import com.hookledger.domain.InvoiceScheduleLineItem;

public record InvoiceScheduleLineItemResponse(
        String id, String description, long amountMinor, int taxRateBasisPoints) {

    public static InvoiceScheduleLineItemResponse from(InvoiceScheduleLineItem item) {
        return new InvoiceScheduleLineItemResponse(
                item.getLineItemId(),
                item.getDescription(),
                item.getAmountMinor(),
                item.getTaxRateBasisPoints());
    }
}
