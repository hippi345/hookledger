package com.hookledger.repository;

import com.hookledger.domain.BankLineCombinationCharge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankLineCombinationChargeRepository extends JpaRepository<BankLineCombinationCharge, String> {

    List<BankLineCombinationCharge> findByBankLineIdOrderByChargeEventIdAsc(String bankLineId);

    boolean existsByBankLineId(String bankLineId);

    boolean existsByChargeEventId(String chargeEventId);
}
