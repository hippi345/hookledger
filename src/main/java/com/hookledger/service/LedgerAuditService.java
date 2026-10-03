package com.hookledger.service;

import com.hookledger.domain.LedgerAuditAction;
import com.hookledger.domain.LedgerAuditEntry;
import com.hookledger.repository.LedgerAuditRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerAuditService {

    public static final String PERIOD_CLOSE_EVENT_ID = "period-close";

    private final LedgerAuditRepository repository;

    public LedgerAuditService(LedgerAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LedgerAuditEntry record(String eventId, LedgerAuditAction action, String detail) {
        if (eventId == null || eventId.isBlank()) {
            throw new InvalidMoneyEventException("Audit event id is required");
        }
        if (eventId.length() > 128) {
            throw new InvalidMoneyEventException("Audit event id is too long");
        }
        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LedgerAuditEntry entry = new LedgerAuditEntry(
                eventId, action, AuditActorContext.currentActor(), recordedAt, detail);
        return repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<LedgerAuditEntry> listForEvent(String eventId) {
        return repository.findByEventIdOrderByRecordedAtAsc(eventId);
    }
}
