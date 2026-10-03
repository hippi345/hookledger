package com.hookledger.api;

import com.hookledger.domain.LedgerAuditAction;
import com.hookledger.domain.LedgerAuditEntry;
import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id, String eventId, LedgerAuditAction action, String actor, Instant recordedAt, String detail) {

    public static AuditEntryResponse from(LedgerAuditEntry entry) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getAction(),
                entry.getActor(),
                entry.getRecordedAt(),
                entry.getDetail());
    }
}
