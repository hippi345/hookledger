package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceCreditNote;
import com.hookledger.domain.InvoicePayment;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoicePaymentRepository;
import com.hookledger.repository.InvoiceRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerStatementService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceCreditNoteRepository invoiceCreditNoteRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;

    public CustomerStatementService(
            InvoiceRepository invoiceRepository,
            InvoiceCreditNoteRepository invoiceCreditNoteRepository,
            InvoicePaymentRepository invoicePaymentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceCreditNoteRepository = invoiceCreditNoteRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
    }

    @Transactional(readOnly = true)
    public CustomerStatement build(String customer, String currency, LocalDate fromInclusive, LocalDate toInclusive) {
        String normalizedCustomer = normalizeCustomer(customer);
        String normalizedCurrency = normalizeCurrency(currency);
        if (fromInclusive.isAfter(toInclusive)) {
            throw new InvalidMoneyEventException("from must not be after to");
        }

        List<Invoice> invoices =
                invoiceRepository.findByCustomerNameAndCurrency(normalizedCustomer, normalizedCurrency);
        List<InvoiceCreditNote> credits =
                invoiceCreditNoteRepository.findByCustomerNameAndCurrency(normalizedCustomer, normalizedCurrency);
        List<InvoicePayment> payments =
                invoicePaymentRepository.findByCustomerNameAndCurrency(normalizedCustomer, normalizedCurrency);

        long startingBalanceMinor = 0;
        for (Invoice invoice : invoices) {
            startingBalanceMinor += balanceDeltaForInvoice(invoice, activityDate(invoice.getCreatedAt()), fromInclusive);
        }
        for (InvoiceCreditNote credit : credits) {
            startingBalanceMinor +=
                    balanceDeltaForCredit(credit, activityDate(credit.getCreatedAt()), fromInclusive);
        }
        for (InvoicePayment payment : payments) {
            startingBalanceMinor +=
                    balanceDeltaForPayment(payment, activityDate(payment.getCreatedAt()), fromInclusive);
        }

        List<ActivityRow> inRange = new ArrayList<>();
        for (Invoice invoice : invoices) {
            LocalDate date = activityDate(invoice.getCreatedAt());
            if (isInRange(date, fromInclusive, toInclusive)) {
                inRange.add(new ActivityRow(
                        invoice.getCreatedAt(),
                        date,
                        "invoice",
                        invoice.getInvoiceId(),
                        invoice.getTotalAmountMinor(),
                        invoice.getTotalAmountMinor()));
            }
        }
        for (InvoiceCreditNote credit : credits) {
            LocalDate date = activityDate(credit.getCreatedAt());
            if (isInRange(date, fromInclusive, toInclusive)) {
                inRange.add(new ActivityRow(
                        credit.getCreatedAt(),
                        date,
                        "credit",
                        credit.getCreditNoteId(),
                        credit.getAmountMinor(),
                        -credit.getAmountMinor()));
            }
        }
        for (InvoicePayment payment : payments) {
            LocalDate date = activityDate(payment.getCreatedAt());
            if (isInRange(date, fromInclusive, toInclusive)) {
                inRange.add(new ActivityRow(
                        payment.getCreatedAt(),
                        date,
                        "payment",
                        payment.getPaymentId(),
                        payment.getAmountMinor(),
                        -payment.getAmountMinor()));
            }
        }

        inRange.sort(Comparator.comparing(ActivityRow::date)
                .thenComparing(ActivityRow::sortInstant)
                .thenComparing(ActivityRow::typeOrder)
                .thenComparing(ActivityRow::id));

        long running = startingBalanceMinor;
        List<CustomerStatementLine> lines = new ArrayList<>();
        for (ActivityRow row : inRange) {
            running += row.balanceDeltaMinor();
            lines.add(new CustomerStatementLine(
                    row.date(), row.type(), row.id(), row.amountMinor(), running));
        }

        return new CustomerStatement(
                normalizedCustomer,
                normalizedCurrency,
                fromInclusive,
                toInclusive,
                startingBalanceMinor,
                running,
                lines);
    }

    private static long balanceDeltaForInvoice(Invoice invoice, LocalDate activityDate, LocalDate fromInclusive) {
        if (activityDate.isBefore(fromInclusive)) {
            return invoice.getTotalAmountMinor();
        }
        return 0;
    }

    private static long balanceDeltaForCredit(InvoiceCreditNote credit, LocalDate activityDate, LocalDate fromInclusive) {
        if (activityDate.isBefore(fromInclusive)) {
            return -credit.getAmountMinor();
        }
        return 0;
    }

    private static long balanceDeltaForPayment(InvoicePayment payment, LocalDate activityDate, LocalDate fromInclusive) {
        if (activityDate.isBefore(fromInclusive)) {
            return -payment.getAmountMinor();
        }
        return 0;
    }

    private static boolean isInRange(LocalDate activityDate, LocalDate fromInclusive, LocalDate toInclusive) {
        return !activityDate.isBefore(fromInclusive) && !activityDate.isAfter(toInclusive);
    }

    private static LocalDate activityDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static String normalizeCustomer(String customer) {
        if (customer == null || customer.isBlank()) {
            throw new InvalidMoneyEventException("Customer is required");
        }
        return URLDecoder.decode(customer, StandardCharsets.UTF_8);
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
        }
        return currency.toUpperCase();
    }

    private record ActivityRow(
            Instant sortInstant,
            LocalDate date,
            String type,
            String id,
            long amountMinor,
            long balanceDeltaMinor) {

        int typeOrder() {
            return switch (type) {
                case "invoice" -> 0;
                case "credit" -> 1;
                case "payment" -> 2;
                default -> 3;
            };
        }
    }

    public record CustomerStatementLine(
            LocalDate date, String type, String id, long amountMinor, long balanceMinor) {}

    public record CustomerStatement(
            String customer,
            String currency,
            LocalDate from,
            LocalDate to,
            long startingBalanceMinor,
            long endingBalanceMinor,
            List<CustomerStatementLine> lines) {}
}
