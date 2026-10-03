package com.hookledger.repository;

import com.hookledger.domain.PayoutSplitCharge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutSplitChargeRepository extends JpaRepository<PayoutSplitCharge, String> {

    List<PayoutSplitCharge> findByPayoutEventIdOrderByChargeEventIdAsc(String payoutEventId);

    boolean existsByPayoutEventId(String payoutEventId);

    boolean existsByChargeEventId(String chargeEventId);
}
