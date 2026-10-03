package com.hookledger.repository;

import com.hookledger.domain.LedgerAuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAuditRepository extends JpaRepository<LedgerAuditEntry, UUID> {

    List<LedgerAuditEntry> findByEventIdOrderByRecordedAtAsc(String eventId);
}
