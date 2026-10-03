package com.hookledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookledger.api.InvoiceResponse;
import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceCreditNote;
import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.domain.InvoiceLateFee;
import com.hookledger.domain.InvoicePayment;
import com.hookledger.domain.InvoiceStatus;
import com.hookledger.domain.LedgerAuditAction;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoiceLateFeeRepository;
import com.hookledger.repository.InvoicePaymentRepository;
import com.hookledger.repository.InvoiceRepository;
import com.hookledger.repository.LedgerEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceCreditNoteRepository invoiceCreditNoteRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final InvoiceLateFeeRepository invoiceLateFeeRepository;
    private final LedgerEventRepository ledgerEventRepository;
    private final ObjectMapper objectMapper;
    private final PeriodCloseService periodCloseService;
    private final LedgerAuditService ledgerAuditService;
    private final InvoicePdfService invoicePdfService;
    private final LedgerService ledgerService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceCreditNoteRepository invoiceCreditNoteRepository,
            InvoicePaymentRepository invoicePaymentRepository,
            InvoiceLateFeeRepository invoiceLateFeeRepository,
            LedgerEventRepository ledgerEventRepository,
            ObjectMapper objectMapper,
            PeriodCloseService periodCloseService,
            LedgerAuditService ledgerAuditService,
            InvoicePdfService invoicePdfService,
            LedgerService ledgerService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceCreditNoteRepository = invoiceCreditNoteRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.invoiceLateFeeRepository = invoiceLateFeeRepository;
        this.ledgerEventRepository = ledgerEventRepository;
        this.objectMapper = objectMapper;
        this.periodCloseService = periodCloseService;
        this.ledgerAuditService = ledgerAuditService;
        this.invoicePdfService = invoicePdfService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public Invoice create(
            String customerName,
            String customerAddress,
            LocalDate dueDate,
            String currency,
            List<LineItemInput> lineItems) {
        validateCustomer(customerName);
        if (dueDate == null) {
            throw new InvoiceException("dueDate is required");
        }
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new InvoiceException("Currency must be a 3-letter ISO code");
        }
        if (lineItems == null || lineItems.isEmpty()) {
            throw new InvoiceException("At least one line item is required");
        }

        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Invoice invoice = new Invoice(
                customerName.trim(),
                normalizeAddress(customerAddress),
                dueDate,
                currency.toUpperCase(),
                createdAt);

        int order = 0;
        for (LineItemInput item : lineItems) {
            if (item.description() == null || item.description().isBlank()) {
                throw new InvoiceException("Line item description is required");
            }
            if (item.amountMinor() <= 0) {
                throw new InvoiceException("Line item amountMinor must be positive");
            }
            invoice.addLineItem(new InvoiceLineItem(invoice, order++, item.description().trim(), item.amountMinor()));
        }

        if (invoice.getTotalAmountMinor() <= 0) {
            throw new InvoiceException("Invoice total must be positive");
        }

        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        return invoiceRepository.findByIdWithLineItems(saved.getInvoiceId()).orElse(saved);
    }

    @Transactional
    public Optional<InvoiceCreditNote> applyCredit(String invoiceId, long amountMinor, String currency) {
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoice -> {
            assertOpenForChanges(invoice);
            if (amountMinor <= 0) {
                throw new InvoiceException("amountMinor must be positive");
            }
            if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
                throw new InvoiceException("Currency must be a 3-letter ISO code");
            }
            String normalizedCurrency = currency.toUpperCase();
            if (!normalizedCurrency.equals(invoice.getCurrency())) {
                throw new InvoiceException("Credit currency must match the invoice");
            }

            long openAmount = openAmountMinor(invoice);
            if (amountMinor > openAmount) {
                throw new InvoiceException("Credit amount exceeds remaining open balance");
            }

            Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            InvoiceCreditNote creditNote =
                    new InvoiceCreditNote(invoice, amountMinor, normalizedCurrency, createdAt);
            return invoiceCreditNoteRepository.saveAndFlush(creditNote);
        });
    }

    @Transactional
    public Optional<InvoiceLateFee> applyLateFee(String invoiceId, long feeMinor, LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoice -> {
            assertOpenForChanges(invoice);
            long openAmount = openAmountMinor(invoice);
            if (openAmount <= 0) {
                throw new InvoiceException("Invoice has no remaining open balance");
            }
            if (feeMinor <= 0) {
                throw new InvoiceException("feeMinor must be positive");
            }
            if (!invoice.getDueDate().isBefore(effectiveAsOf)) {
                throw new InvoiceException("Invoice is not overdue as of the given date");
            }
            if (invoiceLateFeeRepository.existsByInvoiceInvoiceId(invoice.getInvoiceId())) {
                throw new InvoiceException("Late fee already applied to this invoice");
            }

            Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            periodCloseService.assertOpenFor(receivedAt);

            InvoiceLateFee lateFee =
                    new InvoiceLateFee(invoice, feeMinor, invoice.getCurrency(), effectiveAsOf, receivedAt);
            postInvoiceCharge(invoice, feeMinor, lateFee.getLedgerEventId(), receivedAt);
            return invoiceLateFeeRepository.saveAndFlush(lateFee);
        });
    }

    @Transactional
    public Optional<InvoicePayment> applyPayment(String invoiceId, long amountMinor, String currency) {
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoice -> {
            assertOpenForChanges(invoice);
            if (amountMinor <= 0) {
                throw new InvoiceException("amountMinor must be positive");
            }
            if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
                throw new InvoiceException("Currency must be a 3-letter ISO code");
            }
            String normalizedCurrency = currency.toUpperCase();
            if (!normalizedCurrency.equals(invoice.getCurrency())) {
                throw new InvoiceException("Payment currency must match the invoice");
            }

            long openAmount = openAmountMinor(invoice);
            if (amountMinor > openAmount) {
                throw new InvoiceException("Payment amount exceeds remaining open balance");
            }

            Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            periodCloseService.assertOpenFor(receivedAt);

            InvoicePayment payment = new InvoicePayment(invoice, amountMinor, normalizedCurrency, receivedAt);
            postInvoiceCharge(invoice, amountMinor, payment.getLedgerEventId(), receivedAt);

            InvoicePayment saved = invoicePaymentRepository.saveAndFlush(payment);

            if (amountMinor == openAmount) {
                invoice.markPaid(receivedAt, payment.getLedgerEventId());
                invoiceRepository.save(invoice);
            }

            return saved;
        });
    }

    @Transactional
    public Optional<Invoice> voidInvoice(String invoiceId) {
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.voided) {
                throw new InvoiceException("Invoice is already void");
            }
            if (invoice.getStatus() == InvoiceStatus.paid) {
                throw new InvoiceException("Invoice is already paid");
            }
            if (invoicePaymentRepository.countByInvoiceInvoiceId(invoiceId) > 0) {
                throw new InvoiceException("Cannot void an invoice with payments");
            }
            if (invoiceLateFeeRepository.existsByInvoiceInvoiceId(invoiceId)) {
                throw new InvoiceException("Cannot void an invoice with a late fee");
            }

            invoiceCreditNoteRepository.deleteByInvoiceInvoiceId(invoiceId);
            Instant voidedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            invoice.markVoid(voidedAt);
            return invoiceRepository.save(invoice);
        });
    }

    @Transactional
    public Optional<PaymentRefundResult> refundPayment(String invoiceId, String paymentId) {
        return invoicePaymentRepository
                .findByPaymentIdAndInvoiceId(paymentId, invoiceId)
                .map(payment -> {
                    if (payment.isRefunded()) {
                        throw new InvoiceException("Payment is already refunded");
                    }
                    Invoice invoice = payment.getInvoice();
                    if (invoice.getStatus() == InvoiceStatus.voided) {
                        throw new InvoiceException("Invoice is void");
                    }

                    LedgerEvent reversal = ledgerService
                            .reverse(payment.getLedgerEventId())
                            .orElseThrow(() -> new InvoiceException("Ledger event not found for payment"));

                    Instant refundedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
                    payment.markRefunded(refundedAt, reversal.getEventId());
                    invoicePaymentRepository.save(payment);

                    if (invoice.getStatus() == InvoiceStatus.paid) {
                        invoice.markReopen();
                        invoiceRepository.save(invoice);
                    }

                    return new PaymentRefundResult(payment, reversal.getEventId());
                });
    }

    @Transactional(readOnly = true)
    public List<String> searchCustomerNames(String query) {
        if (query == null || query.isBlank()) {
            return invoiceRepository.findDistinctCustomerNames();
        }
        return invoiceRepository.findDistinctCustomerNamesByPrefix(query.trim());
    }

    @Transactional(readOnly = true)
    public InvoiceListPage listInvoices(String customer, String status, String currency, int limit, int offset) {
        if (limit <= 0) {
            throw new InvoiceException("limit must be positive");
        }
        if (offset < 0) {
            throw new InvoiceException("offset must not be negative");
        }

        String customerFilter = customer == null || customer.isBlank() ? null : customer;
        InvoiceStatus statusFilter = parseStatusFilter(status);
        String currencyFilter = null;
        if (currency != null && !currency.isBlank()) {
            if (!MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
                throw new InvoiceException("Currency must be a 3-letter ISO code");
            }
            currencyFilter = currency.toUpperCase();
        }

        int page = offset / limit;
        Page<Invoice> invoices = invoiceRepository.findFiltered(
                customerFilter,
                statusFilter,
                currencyFilter,
                PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt", "invoiceId")));
        List<InvoiceResponse> mapped =
                invoices.getContent().stream().map(InvoiceResponse::from).toList();
        return new InvoiceListPage(mapped, limit, offset, invoices.getTotalElements());
    }

    @Transactional
    public Optional<Invoice> markPaid(String invoiceId) {
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoice -> {
            assertOpenForChanges(invoice);

            long openAmount = openAmountMinor(invoice);
            Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            periodCloseService.assertOpenFor(receivedAt);

            if (openAmount == 0) {
                invoice.markSettledWithoutLedger(receivedAt);
                return invoiceRepository.save(invoice);
            }

            String eventId = invoice.ledgerChargeEventId();
            postInvoiceCharge(invoice, openAmount, eventId, receivedAt);

            invoice.markPaid(receivedAt, eventId);
            return invoiceRepository.save(invoice);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> find(String invoiceId) {
        return invoiceRepository.findByIdWithLineItems(invoiceId);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> renderPdf(String invoiceId) {
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoicePdfService::render);
    }

    private long openAmountMinor(Invoice invoice) {
        return InvoiceOpenBalance.openAmountMinor(
                invoice, invoiceCreditNoteRepository, invoicePaymentRepository);
    }

    private void postInvoiceCharge(Invoice invoice, long chargeAmountMinor, String eventId, Instant receivedAt) {
        if (!MoneyEventValidation.EVENT_ID.matcher(eventId).matches()) {
            throw new InvoiceException("Unable to derive ledger event id for invoice");
        }
        if (ledgerEventRepository.existsById(eventId)) {
            throw new InvoiceException("Ledger event already exists for invoice");
        }

        DoubleEntryResolver.DoubleEntrySides sides =
                new DoubleEntryResolver.DoubleEntrySides(chargeAmountMinor, -chargeAmountMinor);
        DoubleEntryResolver.validateBalanced(sides);

        String rawPayload = buildChargePayload(eventId, invoice, chargeAmountMinor);
        LedgerEvent charge = new LedgerEvent(
                eventId,
                MoneyEventType.charge,
                chargeAmountMinor,
                sides.debitMinor(),
                sides.creditMinor(),
                invoice.getCurrency(),
                rawPayload,
                receivedAt,
                null,
                List.of());

        ledgerEventRepository.saveAndFlush(charge);
        ledgerAuditService.record(eventId, LedgerAuditAction.post, "invoiceId=" + invoice.getInvoiceId());
    }

    private String buildChargePayload(String eventId, Invoice invoice, long chargeAmountMinor) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "id",
                            eventId,
                            "type",
                            MoneyEventType.charge.name(),
                            "amount",
                            chargeAmountMinor,
                            "currency",
                            invoice.getCurrency().toLowerCase(),
                            "invoiceId",
                            invoice.getInvoiceId()));
        } catch (JsonProcessingException e) {
            throw new InvoiceException("Unable to serialize charge payload", e);
        }
    }

    private static void assertOpenForChanges(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.voided) {
            throw new InvoiceException("Invoice is void");
        }
        if (invoice.getStatus() == InvoiceStatus.paid) {
            throw new InvoiceException("Invoice is already paid");
        }
    }

    private static InvoiceStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.fromApiValue(status);
        } catch (IllegalArgumentException e) {
            throw new InvoiceException("status must be open, paid, or void");
        }
    }

    private static void validateCustomer(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            throw new InvoiceException("customerName is required");
        }
    }

    private static String normalizeAddress(String customerAddress) {
        if (customerAddress == null || customerAddress.isBlank()) {
            return null;
        }
        return customerAddress.trim();
    }

    public record LineItemInput(String description, long amountMinor) {}

    public record PaymentRefundResult(InvoicePayment payment, String reversalLedgerEventId) {}

    public record InvoiceListPage(List<InvoiceResponse> invoices, int limit, int offset, long total) {}
}
