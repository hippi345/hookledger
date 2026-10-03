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
@Table(name = "invoice_schedules")
public class InvoiceSchedule {

    @Id
    @Column(name = "schedule_id", nullable = false, updatable = false, length = 36)
    private String scheduleId;

    @Column(name = "customer_name", nullable = false, length = 256)
    private String customerName;

    @Column(name = "customer_address", columnDefinition = "text")
    private String customerAddress;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceScheduleInterval interval;

    @Column(name = "last_generated_due_date")
    private LocalDate lastGeneratedDueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceScheduleLineItem> lineItems = new ArrayList<>();

    protected InvoiceSchedule() {}

    public InvoiceSchedule(
            String customerName,
            String customerAddress,
            String currency,
            InvoiceScheduleInterval interval,
            Instant createdAt) {
        this.scheduleId = UUID.randomUUID().toString();
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.currency = currency;
        this.interval = interval;
        this.createdAt = createdAt;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceScheduleInterval getInterval() {
        return interval;
    }

    public LocalDate getLastGeneratedDueDate() {
        return lastGeneratedDueDate;
    }

    public void setLastGeneratedDueDate(LocalDate lastGeneratedDueDate) {
        this.lastGeneratedDueDate = lastGeneratedDueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InvoiceScheduleLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }

    public void addLineItem(InvoiceScheduleLineItem lineItem) {
        lineItems.add(lineItem);
    }
}
