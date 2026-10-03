package com.hookledger.api;

import com.hookledger.domain.InvoiceScheduleInterval;
import java.util.List;

public record CreateInvoiceScheduleRequest(
        String customerName,
        String customerAddress,
        String currency,
        InvoiceScheduleInterval interval,
        List<InvoiceLineItemRequest> lineItems) {}
