package com.hookledger.api;

import com.hookledger.service.InvoiceOverdueService;
import java.time.LocalDate;
import java.util.List;

public record InvoiceOverdueResponse(LocalDate asOf, List<CustomerOverdueResponse> customers) {

    public static InvoiceOverdueResponse from(InvoiceOverdueService.InvoiceOverdueReport report) {
        return new InvoiceOverdueResponse(
                report.asOf(),
                report.customers().stream().map(CustomerOverdueResponse::from).toList());
    }

    public record CustomerOverdueResponse(
            String customer,
            List<InvoiceOverdueLineResponse> invoices,
            List<CurrencyTotalResponse> totalsByCurrency) {

        static CustomerOverdueResponse from(InvoiceOverdueService.CustomerOverdue customer) {
            return new CustomerOverdueResponse(
                    customer.customer(),
                    customer.invoices().stream().map(InvoiceOverdueLineResponse::from).toList(),
                    customer.totalsByCurrency().stream().map(CurrencyTotalResponse::from).toList());
        }
    }

    public record CurrencyTotalResponse(String currency, long totalAmountMinor) {

        static CurrencyTotalResponse from(InvoiceOverdueService.CurrencyTotal total) {
            return new CurrencyTotalResponse(total.currency(), total.totalAmountMinor());
        }
    }

    public record InvoiceOverdueLineResponse(
            String id, LocalDate dueDate, String currency, long openAmountMinor, long daysPastDue) {

        static InvoiceOverdueLineResponse from(InvoiceOverdueService.InvoiceOverdueLine line) {
            return new InvoiceOverdueLineResponse(
                    line.id(), line.dueDate(), line.currency(), line.openAmountMinor(), line.daysPastDue());
        }
    }
}
