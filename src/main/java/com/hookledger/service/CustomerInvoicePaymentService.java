package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoicePayment;
import com.hookledger.domain.InvoiceStatus;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoicePaymentRepository;
import com.hookledger.repository.InvoiceRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerInvoicePaymentService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceCreditNoteRepository invoiceCreditNoteRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final InvoiceService invoiceService;

    public CustomerInvoicePaymentService(
            InvoiceRepository invoiceRepository,
            InvoiceCreditNoteRepository invoiceCreditNoteRepository,
            InvoicePaymentRepository invoicePaymentRepository,
            InvoiceService invoiceService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceCreditNoteRepository = invoiceCreditNoteRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.invoiceService = invoiceService;
    }

    @Transactional
    public CustomerPaymentResult applyPayment(String customer, long amountMinor, String currency) {
        String normalizedCustomer = normalizeCustomer(customer);
        if (amountMinor <= 0) {
            throw new InvoiceException("amountMinor must be positive");
        }
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new InvoiceException("Currency must be a 3-letter ISO code");
        }
        String normalizedCurrency = currency.toUpperCase();

        List<Invoice> openInvoices = invoiceRepository
                .findOpenByCustomerNameAndCurrencyWithLineItems(
                        normalizedCustomer, normalizedCurrency, InvoiceStatus.open)
                .stream()
                .filter(invoice -> openAmountMinor(invoice) > 0)
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .toList();

        long remaining = amountMinor;
        List<InvoicePayment> applied = new ArrayList<>();
        for (Invoice invoice : openInvoices) {
            if (remaining <= 0) {
                break;
            }
            long open = openAmountMinor(invoice);
            long slice = Math.min(remaining, open);
            InvoicePayment payment = invoiceService
                    .applyPayment(invoice.getInvoiceId(), slice, normalizedCurrency)
                    .orElseThrow(() -> new InvoiceException("Unable to apply payment to invoice"));
            applied.add(payment);
            remaining -= slice;
        }

        if (applied.isEmpty()) {
            throw new InvoiceException("No open invoices for customer");
        }

        long appliedTotal = amountMinor - remaining;
        return new CustomerPaymentResult(normalizedCustomer, normalizedCurrency, appliedTotal, applied);
    }

    private long openAmountMinor(Invoice invoice) {
        return InvoiceOpenBalance.openAmountMinor(
                invoice, invoiceCreditNoteRepository, invoicePaymentRepository);
    }

    private static String normalizeCustomer(String customer) {
        if (customer == null || customer.isBlank()) {
            throw new InvoiceException("Customer is required");
        }
        return URLDecoder.decode(customer, StandardCharsets.UTF_8);
    }

    public record CustomerPaymentResult(
            String customer, String currency, long appliedAmountMinor, List<InvoicePayment> payments) {}
}
