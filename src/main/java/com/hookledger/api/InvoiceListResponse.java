package com.hookledger.api;

import com.hookledger.service.InvoiceService;
import java.util.List;

public record InvoiceListResponse(List<InvoiceResponse> invoices, int limit, int offset, long total) {

    public static InvoiceListResponse from(InvoiceService.InvoiceListPage page) {
        return new InvoiceListResponse(page.invoices(), page.limit(), page.offset(), page.total());
    }
}
