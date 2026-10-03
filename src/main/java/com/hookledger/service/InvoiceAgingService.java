package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceStatus;
import com.hookledger.repository.InvoiceRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceAgingService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceAgingService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceAgingReport buildReport(LocalDate asOf) {
        List<Invoice> openInvoices = invoiceRepository.findAllByStatusWithLineItems(InvoiceStatus.open);

        Map<String, MutableCurrencyAging> byCurrency = new TreeMap<>();
        for (Invoice invoice : openInvoices) {
            AgingBucket bucket = classifyBucket(invoice.getDueDate(), asOf);
            long openAmountMinor = openAmountMinor(invoice);
            byCurrency
                    .computeIfAbsent(invoice.getCurrency(), MutableCurrencyAging::new)
                    .add(bucket, toLine(invoice, openAmountMinor));
        }

        List<CurrencyAging> currencies = byCurrency.values().stream()
                .map(MutableCurrencyAging::toImmutable)
                .toList();

        return new InvoiceAgingReport(asOf, currencies);
    }

    static AgingBucket classifyBucket(LocalDate dueDate, LocalDate asOf) {
        long daysPastDue = ChronoUnit.DAYS.between(dueDate, asOf);
        if (daysPastDue <= 0) {
            return AgingBucket.CURRENT;
        }
        if (daysPastDue <= 30) {
            return AgingBucket.DAYS_1_TO_30;
        }
        if (daysPastDue <= 60) {
            return AgingBucket.DAYS_31_TO_60;
        }
        if (daysPastDue <= 90) {
            return AgingBucket.DAYS_61_TO_90;
        }
        return AgingBucket.OVER_90;
    }

    private static long openAmountMinor(Invoice invoice) {
        return invoice.getTotalAmountMinor();
    }

    private static InvoiceAgingLine toLine(Invoice invoice, long openAmountMinor) {
        return new InvoiceAgingLine(
                invoice.getInvoiceId(),
                invoice.getCustomerName(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                openAmountMinor);
    }

    public enum AgingBucket {
        CURRENT,
        DAYS_1_TO_30,
        DAYS_31_TO_60,
        DAYS_61_TO_90,
        OVER_90
    }

    public record InvoiceAgingReport(LocalDate asOf, List<CurrencyAging> currencies) {}

    public record CurrencyAging(
            String currency,
            BucketTotals current,
            BucketTotals days1To30,
            BucketTotals days31To60,
            BucketTotals days61To90,
            BucketTotals over90,
            long grandTotalAmountMinor) {}

    public record BucketTotals(List<InvoiceAgingLine> invoices, long totalAmountMinor) {}

    public record InvoiceAgingLine(
            String id, String customer, LocalDate dueDate, String currency, long openAmountMinor) {}

    private static final class MutableCurrencyAging {
        private final String currency;
        private final Map<AgingBucket, List<InvoiceAgingLine>> lines = new LinkedHashMap<>();

        private MutableCurrencyAging(String currency) {
            this.currency = currency;
            for (AgingBucket bucket : AgingBucket.values()) {
                lines.put(bucket, new ArrayList<>());
            }
        }

        private void add(AgingBucket bucket, InvoiceAgingLine line) {
            lines.get(bucket).add(line);
        }

        private CurrencyAging toImmutable() {
            long grandTotal = 0;
            Map<AgingBucket, BucketTotals> totals = new LinkedHashMap<>();
            for (AgingBucket bucket : AgingBucket.values()) {
                List<InvoiceAgingLine> bucketLines = lines.get(bucket).stream()
                        .sorted(Comparator.comparing(InvoiceAgingLine::dueDate).thenComparing(InvoiceAgingLine::id))
                        .toList();
                long bucketTotal = bucketLines.stream().mapToLong(InvoiceAgingLine::openAmountMinor).sum();
                grandTotal += bucketTotal;
                totals.put(bucket, new BucketTotals(bucketLines, bucketTotal));
            }
            return new CurrencyAging(
                    currency,
                    totals.get(AgingBucket.CURRENT),
                    totals.get(AgingBucket.DAYS_1_TO_30),
                    totals.get(AgingBucket.DAYS_31_TO_60),
                    totals.get(AgingBucket.DAYS_61_TO_90),
                    totals.get(AgingBucket.OVER_90),
                    grandTotal);
        }
    }
}
