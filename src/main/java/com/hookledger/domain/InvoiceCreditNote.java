package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_credit_notes")
public class InvoiceCreditNote {

    @Id
    @Column(name = "credit_note_id", nullable = false, updatable = false, length = 36)
    private String creditNoteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceCreditNote() {}

    public InvoiceCreditNote(Invoice invoice, long amountMinor, String currency, Instant createdAt) {
        this.creditNoteId = UUID.randomUUID().toString();
        this.invoice = invoice;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getCreditNoteId() {
        return creditNoteId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
