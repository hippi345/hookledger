package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.domain.InvoiceSchedule;
import com.hookledger.domain.InvoiceScheduleInterval;
import com.hookledger.domain.InvoiceScheduleLineItem;
import com.hookledger.repository.InvoiceScheduleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceScheduleService {

    private final InvoiceScheduleRepository invoiceScheduleRepository;
    private final InvoiceService invoiceService;

    public InvoiceScheduleService(InvoiceScheduleRepository invoiceScheduleRepository, InvoiceService invoiceService) {
        this.invoiceScheduleRepository = invoiceScheduleRepository;
        this.invoiceService = invoiceService;
    }

    @Transactional
    public InvoiceSchedule create(
            String customerName,
            String customerAddress,
            String currency,
            InvoiceScheduleInterval interval,
            List<InvoiceService.LineItemInput> lineItems) {
        if (interval != InvoiceScheduleInterval.monthly) {
            throw new InvoiceException("Only monthly interval is supported");
        }
        if (customerName == null || customerName.isBlank()) {
            throw new InvoiceException("customerName is required");
        }
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new InvoiceException("Currency must be a 3-letter ISO code");
        }
        if (lineItems == null || lineItems.isEmpty()) {
            throw new InvoiceException("At least one line item is required");
        }

        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        InvoiceSchedule schedule = new InvoiceSchedule(
                customerName.trim(),
                normalizeAddress(customerAddress),
                currency.toUpperCase(),
                interval,
                createdAt);

        int order = 0;
        for (InvoiceService.LineItemInput item : lineItems) {
            if (item.description() == null || item.description().isBlank()) {
                throw new InvoiceException("Line item description is required");
            }
            if (item.amountMinor() <= 0) {
                throw new InvoiceException("Line item amountMinor must be positive");
            }
            if (item.taxRateBasisPoints() < 0) {
                throw new InvoiceException("taxRateBasisPoints must not be negative");
            }
            schedule.addLineItem(new InvoiceScheduleLineItem(
                    schedule,
                    order++,
                    item.description().trim(),
                    item.amountMinor(),
                    item.taxRateBasisPoints()));
        }

        InvoiceSchedule saved = invoiceScheduleRepository.saveAndFlush(schedule);
        return invoiceScheduleRepository.findByIdWithLineItems(saved.getScheduleId()).orElse(saved);
    }

    @Transactional
    public Optional<Invoice> generate(String scheduleId) {
        return invoiceScheduleRepository.findByIdWithLineItems(scheduleId).map(schedule -> {
            LocalDate dueDate;
            if (schedule.getLastGeneratedDueDate() == null) {
                dueDate = LocalDate.now().plusMonths(1);
            } else {
                dueDate = schedule.getLastGeneratedDueDate().plusMonths(1);
            }

            List<InvoiceService.LineItemInput> lineItems = schedule.getLineItems().stream()
                    .map(item -> new InvoiceService.LineItemInput(
                            item.getDescription(), item.getAmountMinor(), item.getTaxRateBasisPoints()))
                    .toList();

            Invoice invoice = invoiceService.create(
                    schedule.getCustomerName(),
                    schedule.getCustomerAddress(),
                    dueDate,
                    schedule.getCurrency(),
                    lineItems);

            schedule.setLastGeneratedDueDate(dueDate);
            invoiceScheduleRepository.save(schedule);
            return invoice;
        });
    }

    private static String normalizeAddress(String customerAddress) {
        if (customerAddress == null || customerAddress.isBlank()) {
            return null;
        }
        return customerAddress.trim();
    }
}
