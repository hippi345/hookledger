package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_lines")
public class BankLine {

    @Id
    @Column(name = "bank_line_id", nullable = false, updatable = false, length = 36)
    private String bankLineId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "line_date", nullable = false)
    private LocalDate lineDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "matched_ledger_event_id", length = 128)
    private String matchedLedgerEventId;

    @Column(name = "matched_at")
    private Instant matchedAt;

    protected BankLine() {}

    public BankLine(long amountMinor, String currency, LocalDate lineDate, Instant createdAt) {
        this.bankLineId = UUID.randomUUID().toString();
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.lineDate = lineDate;
        this.createdAt = createdAt;
    }

    public String getBankLineId() {
        return bankLineId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getLineDate() {
        return lineDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getMatchedLedgerEventId() {
        return matchedLedgerEventId;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public boolean isMatched() {
        return matchedLedgerEventId != null;
    }

    public void matchTo(String ledgerEventId, Instant matchedAt) {
        this.matchedLedgerEventId = ledgerEventId;
        this.matchedAt = matchedAt;
    }
}
