package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "invoice_late_fees",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_late_fees_invoice_id", columnNames = "invoice_id"))
public class InvoiceLateFee {

    @Id
    @Column(name = "late_fee_id", nullable = false, updatable = false, length = 36)
    private String lateFeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "as_of", nullable = false, updatable = false)
    private LocalDate asOf;

    @Column(name = "ledger_event_id", nullable = false, updatable = false, length = 128)
    private String ledgerEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceLateFee() {}

    public InvoiceLateFee(Invoice invoice, long feeMinor, String currency, LocalDate asOf, Instant createdAt) {
        this.lateFeeId = UUID.randomUUID().toString();
        this.invoice = invoice;
        this.feeMinor = feeMinor;
        this.currency = currency;
        this.asOf = asOf;
        this.createdAt = createdAt;
        this.ledgerEventId = ledgerChargeEventId();
    }

    public String getLateFeeId() {
        return lateFeeId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public long getFeeMinor() {
        return feeMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getAsOf() {
        return asOf;
    }

    public String getLedgerEventId() {
        return ledgerEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String ledgerChargeEventId() {
        return "invlate_" + lateFeeId.replace("-", "");
    }
}
