package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem {

    @Id
    @Column(name = "line_item_id", nullable = false, updatable = false, length = 36)
    private String lineItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private int lineOrder;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected InvoiceLineItem() {}

    public InvoiceLineItem(Invoice invoice, int lineOrder, String description, long amountMinor) {
        this.lineItemId = UUID.randomUUID().toString();
        this.invoice = invoice;
        this.lineOrder = lineOrder;
        this.description = description;
        this.amountMinor = amountMinor;
    }

    public String getLineItemId() {
        return lineItemId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public int getLineOrder() {
        return lineOrder;
    }

    public String getDescription() {
        return description;
    }

    public long getAmountMinor() {
        return amountMinor;
    }
}
