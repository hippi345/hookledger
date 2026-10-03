package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceStatus;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoicePaymentRepository;
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
public class InvoiceOverdueService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceCreditNoteRepository invoiceCreditNoteRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;

    public InvoiceOverdueService(
            InvoiceRepository invoiceRepository,
            InvoiceCreditNoteRepository invoiceCreditNoteRepository,
            InvoicePaymentRepository invoicePaymentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceCreditNoteRepository = invoiceCreditNoteRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceOverdueReport buildReport(LocalDate asOf) {
        List<Invoice> openInvoices = invoiceRepository.findAllByStatusWithLineItems(InvoiceStatus.open);

        Map<String, MutableCustomerOverdue> byCustomer = new TreeMap<>();
        for (Invoice invoice : openInvoices) {
            if (!invoice.getDueDate().isBefore(asOf)) {
                continue;
            }
            long openAmountMinor = InvoiceOpenBalance.openAmountMinor(
                    invoice, invoiceCreditNoteRepository, invoicePaymentRepository);
            if (openAmountMinor <= 0) {
                continue;
            }
            long daysPastDue = ChronoUnit.DAYS.between(invoice.getDueDate(), asOf);
            byCustomer
                    .computeIfAbsent(invoice.getCustomerName(), MutableCustomerOverdue::new)
                    .add(toLine(invoice, openAmountMinor, daysPastDue));
        }

        List<CustomerOverdue> customers = byCustomer.values().stream()
                .map(MutableCustomerOverdue::toImmutable)
                .toList();

        return new InvoiceOverdueReport(asOf, customers);
    }

    private static InvoiceOverdueLine toLine(Invoice invoice, long openAmountMinor, long daysPastDue) {
        return new InvoiceOverdueLine(
                invoice.getInvoiceId(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                openAmountMinor,
                daysPastDue);
    }

    public record InvoiceOverdueReport(LocalDate asOf, List<CustomerOverdue> customers) {}

    public record CustomerOverdue(
            String customer, List<InvoiceOverdueLine> invoices, List<CurrencyTotal> totalsByCurrency) {}

    public record CurrencyTotal(String currency, long totalAmountMinor) {}

    public record InvoiceOverdueLine(
            String id, LocalDate dueDate, String currency, long openAmountMinor, long daysPastDue) {}

    private static final class MutableCustomerOverdue {
        private final String customer;
        private final List<InvoiceOverdueLine> invoices = new ArrayList<>();

        private MutableCustomerOverdue(String customer) {
            this.customer = customer;
        }

        private void add(InvoiceOverdueLine line) {
            invoices.add(line);
        }

        private CustomerOverdue toImmutable() {
            List<InvoiceOverdueLine> sorted = invoices.stream()
                    .sorted(Comparator.comparing(InvoiceOverdueLine::dueDate).thenComparing(InvoiceOverdueLine::id))
                    .toList();

            Map<String, Long> totals = new LinkedHashMap<>();
            for (InvoiceOverdueLine line : sorted) {
                totals.merge(line.currency(), line.openAmountMinor(), Long::sum);
            }
            List<CurrencyTotal> totalsByCurrency = totals.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new CurrencyTotal(entry.getKey(), entry.getValue()))
                    .toList();

            return new CustomerOverdue(customer, sorted, totalsByCurrency);
        }
    }
}
