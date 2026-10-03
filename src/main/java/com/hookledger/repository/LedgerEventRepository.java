package com.hookledger.repository;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    List<LedgerEvent> findAllByOrderByReceivedAtDesc();

    Page<LedgerEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<LedgerEvent> findByTypeOrderByReceivedAtDesc(MoneyEventType type, Pageable pageable);

    Page<LedgerEvent> findByCurrencyOrderByReceivedAtDesc(String currency, Pageable pageable);

    Page<LedgerEvent> findByTypeAndCurrencyOrderByReceivedAtDesc(
            MoneyEventType type, String currency, Pageable pageable);

    List<LedgerEvent> findAllByOrderByReceivedAtAsc();

    List<LedgerEvent> findByCurrencyAndReceivedAtLessThanOrderByReceivedAtAsc(String currency, Instant before);

    List<LedgerEvent> findByCurrencyAndReceivedAtBetweenOrderByReceivedAtAsc(
            String currency, Instant fromInclusive, Instant toInclusive);

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
                CASE
                    WHEN e.reversesEventId IS NOT NULL THEN
                        CASE e.type
                            WHEN com.hookledger.domain.MoneyEventType.charge THEN -e.amountMinor
                            WHEN com.hookledger.domain.MoneyEventType.refund THEN e.amountMinor
                            ELSE 0
                        END
                    ELSE
                        CASE e.type
                            WHEN com.hookledger.domain.MoneyEventType.charge THEN e.amountMinor
                            WHEN com.hookledger.domain.MoneyEventType.refund THEN -e.amountMinor
                            ELSE 0
                        END
                END
            ), 0)
            FROM LedgerEvent e
            WHERE e.currency = :currency
            """)
    long balanceMinorByCurrency(@Param("currency") String currency);

    @Query("SELECT DISTINCT e.currency FROM LedgerEvent e ORDER BY e.currency ASC")
    List<String> findDistinctCurrencies();
}
