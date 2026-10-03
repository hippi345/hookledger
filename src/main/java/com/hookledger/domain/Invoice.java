package com.hookledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @Column(name = "invoice_id", nullable = false, updatable = false, length = 36)
    private String invoiceId;

    @Column(name = "customer_name", nullable = false, length = 256)
    private String customerName;

    @Column(name = "customer_address", columnDefinition = "text")
    private String customerAddress;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status;

    @Column(name = "total_amount_minor", nullable = false)
    private long totalAmountMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "ledger_event_id", length = 128)
    private String ledgerEventId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    protected Invoice() {}

    public Invoice(
            String customerName,
            String customerAddress,
            LocalDate dueDate,
            String currency,
            Instant createdAt) {
        this.invoiceId = UUID.randomUUID().toString();
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.dueDate = dueDate;
        this.currency = currency;
        this.status = InvoiceStatus.open;
        this.totalAmountMinor = 0;
        this.createdAt = createdAt;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public long getTotalAmountMinor() {
        return totalAmountMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getLedgerEventId() {
        return ledgerEventId;
    }

    public List<InvoiceLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }

    public void addLineItem(InvoiceLineItem lineItem) {
        lineItems.add(lineItem);
        totalAmountMinor += InvoiceSalesTax.lineTotalMinor(lineItem.getAmountMinor(), lineItem.getTaxRateBasisPoints());
    }

    public void markPaid(Instant paidAt, String ledgerEventId) {
        this.status = InvoiceStatus.paid;
        this.paidAt = paidAt;
        this.ledgerEventId = ledgerEventId;
    }

    public void markSettledWithoutLedger(Instant paidAt) {
        this.status = InvoiceStatus.paid;
        this.paidAt = paidAt;
        this.ledgerEventId = null;
    }

    public String ledgerChargeEventId() {
        return "inv_" + invoiceId.replace("-", "");
    }

    public String writeOffLedgerEventId() {
        return "invwo_" + invoiceId.replace("-", "");
    }
}
