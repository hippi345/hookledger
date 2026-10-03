package com.hookledger.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hookledger.service.InvoiceAgingService;
import java.time.LocalDate;
import java.util.List;

public record InvoiceAgingResponse(LocalDate asOf, List<CurrencyAgingResponse> currencies) {

    public static InvoiceAgingResponse from(InvoiceAgingService.InvoiceAgingReport report) {
        return new InvoiceAgingResponse(
                report.asOf(),
                report.currencies().stream().map(CurrencyAgingResponse::from).toList());
    }

    public record CurrencyAgingResponse(
            String currency,
            BucketResponse current,
            @JsonProperty("30") BucketResponse days1To30,
            @JsonProperty("60") BucketResponse days31To60,
            @JsonProperty("90") BucketResponse days61To90,
            BucketResponse over90,
            long grandTotalAmountMinor) {

        static CurrencyAgingResponse from(InvoiceAgingService.CurrencyAging currency) {
            return new CurrencyAgingResponse(
                    currency.currency(),
                    BucketResponse.from(currency.current()),
                    BucketResponse.from(currency.days1To30()),
                    BucketResponse.from(currency.days31To60()),
                    BucketResponse.from(currency.days61To90()),
                    BucketResponse.from(currency.over90()),
                    currency.grandTotalAmountMinor());
        }
    }

    public record BucketResponse(List<InvoiceAgingLineResponse> invoices, long totalAmountMinor) {

        static BucketResponse from(InvoiceAgingService.BucketTotals bucket) {
            return new BucketResponse(
                    bucket.invoices().stream().map(InvoiceAgingLineResponse::from).toList(),
                    bucket.totalAmountMinor());
        }
    }

    public record InvoiceAgingLineResponse(
            String id, String customer, LocalDate dueDate, String currency, long openAmountMinor) {

        static InvoiceAgingLineResponse from(InvoiceAgingService.InvoiceAgingLine line) {
            return new InvoiceAgingLineResponse(
                    line.id(), line.customer(), line.dueDate(), line.currency(), line.openAmountMinor());
        }
    }
}
