package com.hookledger.service;

import com.hookledger.domain.LedgerAuditAction;
import com.hookledger.domain.PeriodClose;
import com.hookledger.repository.PeriodCloseRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodCloseService {

    private final PeriodCloseRepository repository;
    private final LedgerAuditService ledgerAuditService;

    public PeriodCloseService(PeriodCloseRepository repository, LedgerAuditService ledgerAuditService) {
        this.repository = repository;
        this.ledgerAuditService = ledgerAuditService;
    }

    @Transactional(readOnly = true)
    public Optional<Instant> lockedThrough() {
        return repository.findById(PeriodClose.SINGLETON_ID).map(PeriodClose::getLockedThrough);
    }

    @Transactional
    public Instant closeThrough(Instant through) {
        if (through == null) {
            throw new InvalidMoneyEventException("Close date (through) is required");
        }
        Instant normalized = through.truncatedTo(ChronoUnit.MICROS);
        PeriodClose row = repository
                .findById(PeriodClose.SINGLETON_ID)
                .orElseGet(() -> new PeriodClose(normalized));
        Instant previous = row.getLockedThrough();
        if (previous != null && normalized.isBefore(previous)) {
            throw new InvalidMoneyEventException("Close date must not be before the existing lock");
        }
        row.setLockedThrough(normalized);
        Instant locked = repository.save(row).getLockedThrough();
        ledgerAuditService.record(
                LedgerAuditService.PERIOD_CLOSE_EVENT_ID,
                LedgerAuditAction.period_close,
                "lockedThrough=" + locked);
        return locked;
    }

    @Transactional(readOnly = true)
    public void assertOpenFor(Instant eventInstant) {
        lockedThrough().ifPresent(lock -> {
            Instant normalizedEvent = eventInstant.truncatedTo(ChronoUnit.MICROS);
            if (!normalizedEvent.isAfter(lock)) {
                throw new PeriodClosedException(
                        "Event date is on or before the closed period (locked through " + lock + ")");
            }
        });
    }

    @Transactional(readOnly = true)
    public Instant effectiveReversalTimestamp(Instant originalReceivedAt, Instant preferred) {
        Instant candidate = preferred != null ? preferred : Instant.now();
        Optional<Instant> lock = lockedThrough();
        if (lock.isEmpty()) {
            return candidate.truncatedTo(ChronoUnit.MICROS);
        }
        Instant locked = lock.get();
        Instant normalizedOriginal = originalReceivedAt.truncatedTo(ChronoUnit.MICROS);
        if (normalizedOriginal.isAfter(locked)) {
            return candidate.truncatedTo(ChronoUnit.MICROS);
        }
        Instant afterLock = locked.plus(1, ChronoUnit.MICROS);
        if (candidate.isAfter(locked)) {
            return candidate.truncatedTo(ChronoUnit.MICROS);
        }
        return afterLock;
    }
}
