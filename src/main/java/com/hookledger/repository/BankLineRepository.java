package com.hookledger.repository;

import com.hookledger.domain.BankLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankLineRepository extends JpaRepository<BankLine, String> {

    List<BankLine> findByMatchedLedgerEventIdIsNullOrderByCreatedAtAsc();

    boolean existsByMatchedLedgerEventId(String ledgerEventId);
}
