package com.hookledger.repository;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    List<LedgerEvent> findAllByOrderByReceivedAtDesc();

    List<LedgerEvent> findAllByOrderByReceivedAtAsc();

    List<LedgerEvent> findByTypeOrderByAmountMinorDesc(MoneyEventType type);

    @Query(
            """
            SELECT e FROM LedgerEvent e
            WHERE e.refundChargeId = :chargeId
               OR e.payoutChargeIdsJson LIKE CONCAT('%"', :chargeId, '"%')
            """)
    List<LedgerEvent> findReferencingEventId(@Param("chargeId") String chargeId);

    @Query(
            """
            SELECT COALESCE(SUM(
                CASE e.type
                    WHEN com.hookledger.domain.MoneyEventType.charge THEN e.amountMinor
                    WHEN com.hookledger.domain.MoneyEventType.refund THEN -e.amountMinor
                    ELSE 0
                END
            ), 0)
            FROM LedgerEvent e
            WHERE e.currency = :currency
            """)
    long balanceMinorByCurrency(@Param("currency") String currency);
}
