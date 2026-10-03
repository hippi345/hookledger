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
@Table(name = "invoice_schedule_line_items")
public class InvoiceScheduleLineItem {

    @Id
    @Column(name = "line_item_id", nullable = false, updatable = false, length = 36)
    private String lineItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private InvoiceSchedule schedule;

    @Column(nullable = false)
    private int lineOrder;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "tax_rate_basis_points", nullable = false)
    private int taxRateBasisPoints;

    protected InvoiceScheduleLineItem() {}

    public InvoiceScheduleLineItem(
            InvoiceSchedule schedule,
            int lineOrder,
            String description,
            long amountMinor,
            int taxRateBasisPoints) {
        this.lineItemId = UUID.randomUUID().toString();
        this.schedule = schedule;
        this.lineOrder = lineOrder;
        this.description = description;
        this.amountMinor = amountMinor;
        this.taxRateBasisPoints = taxRateBasisPoints;
    }

    public String getLineItemId() {
        return lineItemId;
    }

    public InvoiceSchedule getSchedule() {
        return schedule;
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

    public int getTaxRateBasisPoints() {
        return taxRateBasisPoints;
    }
}
