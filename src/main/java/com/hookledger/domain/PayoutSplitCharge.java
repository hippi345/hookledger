package com.hookledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "payout_split_charges")
public class PayoutSplitCharge {

    @Id
    @Column(name = "link_id", nullable = false, updatable = false, length = 36)
    private String linkId;

    @Column(name = "payout_event_id", nullable = false, length = 128)
    private String payoutEventId;

    @Column(name = "charge_event_id", nullable = false, unique = true, length = 128)
    private String chargeEventId;

    protected PayoutSplitCharge() {}

    public PayoutSplitCharge(String payoutEventId, String chargeEventId) {
        this.linkId = UUID.randomUUID().toString();
        this.payoutEventId = payoutEventId;
        this.chargeEventId = chargeEventId;
    }

    public String getLinkId() {
        return linkId;
    }

    public String getPayoutEventId() {
        return payoutEventId;
    }

    public String getChargeEventId() {
        return chargeEventId;
    }
}
