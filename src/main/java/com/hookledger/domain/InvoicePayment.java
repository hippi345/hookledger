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
@Table(name = "invoice_payments")
public class InvoicePayment {

    @Id
    @Column(name = "payment_id", nullable = false, updatable = false, length = 36)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "ledger_event_id", nullable = false, updatable = false, length = 128)
    private String ledgerEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoicePayment() {}

    public InvoicePayment(Invoice invoice, long amountMinor, String currency, Instant createdAt) {
        this.paymentId = UUID.randomUUID().toString();
        this.invoice = invoice;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = createdAt;
        this.ledgerEventId = ledgerChargeEventId();
    }

    public String getPaymentId() {
        return paymentId;
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

    public String getLedgerEventId() {
        return ledgerEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String ledgerChargeEventId() {
        return "invpay_" + paymentId.replace("-", "");
    }
}
