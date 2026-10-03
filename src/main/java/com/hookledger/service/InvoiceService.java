package com.hookledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceCreditNote;
import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.domain.InvoiceStatus;
import com.hookledger.domain.LedgerAuditAction;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.InvoiceCreditNoteRepository;
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
    private final LedgerEventRepository ledgerEventRepository;
    private final ObjectMapper objectMapper;
    private final PeriodCloseService periodCloseService;
    private final LedgerAuditService ledgerAuditService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceCreditNoteRepository invoiceCreditNoteRepository,
            LedgerEventRepository ledgerEventRepository,
            ObjectMapper objectMapper,
            PeriodCloseService periodCloseService,
            LedgerAuditService ledgerAuditService,
            InvoicePdfService invoicePdfService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceCreditNoteRepository = invoiceCreditNoteRepository;
        this.ledgerEventRepository = ledgerEventRepository;
        this.objectMapper = objectMapper;
        this.periodCloseService = periodCloseService;
        this.ledgerAuditService = ledgerAuditService;
        this.invoicePdfService = invoicePdfService;
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
            if (invoice.getStatus() == InvoiceStatus.paid) {
                throw new InvoiceException("Invoice is already paid");
            }
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

            long openAmount = InvoiceOpenBalance.openAmountMinor(invoice, invoiceCreditNoteRepository);
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
    public Optional<Invoice> markPaid(String invoiceId) {
        return invoiceRepository.findByIdWithLineItems(invoiceId).map(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.paid) {
                throw new InvoiceException("Invoice is already paid");
            }

            long openAmount = InvoiceOpenBalance.openAmountMinor(invoice, invoiceCreditNoteRepository);
            Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            periodCloseService.assertOpenFor(receivedAt);

            if (openAmount == 0) {
                invoice.markSettledWithoutLedger(receivedAt);
                return invoiceRepository.save(invoice);
            }

            String eventId = invoice.ledgerChargeEventId();
            if (!MoneyEventValidation.EVENT_ID.matcher(eventId).matches()) {
                throw new InvoiceException("Unable to derive ledger event id for invoice");
            }
            if (ledgerEventRepository.existsById(eventId)) {
                throw new InvoiceException("Ledger event already exists for invoice");
            }

            DoubleEntryResolver.DoubleEntrySides sides =
                    new DoubleEntryResolver.DoubleEntrySides(openAmount, -openAmount);
            DoubleEntryResolver.validateBalanced(sides);

            String rawPayload = buildChargePayload(eventId, invoice, openAmount);
            LedgerEvent charge = new LedgerEvent(
                    eventId,
                    MoneyEventType.charge,
                    openAmount,
                    sides.debitMinor(),
                    sides.creditMinor(),
                    invoice.getCurrency(),
                    rawPayload,
                    receivedAt,
                    null,
                    List.of());

            ledgerEventRepository.saveAndFlush(charge);
            ledgerAuditService.record(eventId, LedgerAuditAction.post, "invoiceId=" + invoice.getInvoiceId());

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
}
