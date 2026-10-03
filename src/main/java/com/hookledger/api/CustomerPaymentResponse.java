package com.hookledger.api;

import com.hookledger.service.CustomerInvoicePaymentService;
import java.util.List;

public record CustomerPaymentResponse(
        String customer, String currency, long appliedAmountMinor, List<InvoicePaymentResponse> payments) {

    public static CustomerPaymentResponse from(CustomerInvoicePaymentService.CustomerPaymentResult result) {
        return new CustomerPaymentResponse(
                result.customer(),
                result.currency(),
                result.appliedAmountMinor(),
                result.payments().stream().map(InvoicePaymentResponse::from).toList());
    }
}
