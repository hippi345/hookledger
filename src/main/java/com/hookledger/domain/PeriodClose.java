package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "period_close")
public class PeriodClose {

    public static final int SINGLETON_ID = 1;

    @Id
    private int id = SINGLETON_ID;

    @Column(name = "locked_through", nullable = false)
    private Instant lockedThrough;

    protected PeriodClose() {}

    public PeriodClose(Instant lockedThrough) {
        this.id = SINGLETON_ID;
        this.lockedThrough = lockedThrough;
    }

    public Instant getLockedThrough() {
        return lockedThrough;
    }

    public void setLockedThrough(Instant lockedThrough) {
        this.lockedThrough = lockedThrough;
    }
}
