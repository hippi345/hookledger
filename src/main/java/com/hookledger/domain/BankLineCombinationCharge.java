package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "bank_line_combination_charges")
public class BankLineCombinationCharge {

    @Id
    @Column(name = "link_id", nullable = false, updatable = false, length = 36)
    private String linkId;

    @Column(name = "bank_line_id", nullable = false, length = 36)
    private String bankLineId;

    @Column(name = "charge_event_id", nullable = false, unique = true, length = 128)
    private String chargeEventId;

    protected BankLineCombinationCharge() {}

    public BankLineCombinationCharge(String bankLineId, String chargeEventId) {
        this.linkId = UUID.randomUUID().toString();
        this.bankLineId = bankLineId;
        this.chargeEventId = chargeEventId;
    }

    public String getLinkId() {
        return linkId;
    }

    public String getBankLineId() {
        return bankLineId;
    }

    public String getChargeEventId() {
        return chargeEventId;
    }
}
