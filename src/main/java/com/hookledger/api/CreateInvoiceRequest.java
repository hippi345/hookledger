package com.hookledger.api;

import java.time.LocalDate;
import java.util.List;

public record CreateInvoiceRequest(
        String customerName,
        String customerAddress,
        LocalDate dueDate,
        String currency,
        List<InvoiceLineItemRequest> lineItems) {}
