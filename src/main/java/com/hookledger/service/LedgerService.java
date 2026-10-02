package com.hookledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookledger.domain.EventStateFlags;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.domain.ReplayIdempotency;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.ReplayIdempotencyRepository;
import com.hookledger.webhook.MoneyEventPayload;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerEventRepository repository;
    private final ReplayIdempotencyRepository replayIdempotencyRepository;
    private final ObjectMapper objectMapper;
    private final SettlementGraphService settlementGraphService;
    private final PeriodCloseService periodCloseService;
    private final Deque<String> replayUndoStack = new ArrayDeque<>();

    public LedgerService(
            LedgerEventRepository repository,
            ReplayIdempotencyRepository replayIdempotencyRepository,
            ObjectMapper objectMapper,
            SettlementGraphService settlementGraphService,
            PeriodCloseService periodCloseService) {
        this.repository = repository;
        this.replayIdempotencyRepository = replayIdempotencyRepository;
        this.objectMapper = objectMapper;
        this.settlementGraphService = settlementGraphService;
        this.periodCloseService = periodCloseService;
    }

    @Transactional
    public IngestResult ingest(String rawPayload) {
        MoneyEventPayload payload = parsePayload(rawPayload);
        validatePayload(payload);
        DoubleEntryResolver.DoubleEntrySides sides = DoubleEntryResolver.resolve(payload);
        DoubleEntryResolver.validateBalanced(sides);
        List<String> settlementTargets = settlementTargets(payload);

        Optional<LedgerEvent> existing = repository.findById(payload.id());
        if (existing.isPresent()) {
            LedgerEvent event = existing.get();
            event.markDuplicateIngest();
            repository.save(event);
            return new IngestResult(IngestStatus.ALREADY_EXISTS, event);
        }

        settlementGraphService.validateReferencedCharges(payload.id(), payload.type(), settlementTargets);
        settlementGraphService.validateReferencesAcyclic(payload.id(), settlementTargets);

        Instant receivedAt = payload.effectiveAt() != null ? payload.effectiveAt() : Instant.now();
        periodCloseService.assertOpenFor(receivedAt);

        LedgerEvent event = new LedgerEvent(
                payload.id(),
                payload.type(),
                payload.amount(),
                sides.debitMinor(),
                sides.creditMinor(),
                payload.currency().toUpperCase(),
                rawPayload,
                receivedAt.truncatedTo(ChronoUnit.MICROS),
                payload.type() == MoneyEventType.refund ? payload.chargeId() : null,
                payload.type() == MoneyEventType.payout ? payload.chargeIds() : List.of());

        try {
            repository.saveAndFlush(event);
            return new IngestResult(IngestStatus.CREATED, event);
        } catch (DataIntegrityViolationException ex) {
            return repository
                    .findById(payload.id())
                    .map(e -> {
                        e.markDuplicateIngest();
                        repository.save(e);
                        return new IngestResult(IngestStatus.ALREADY_EXISTS, e);
                    })
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public List<LedgerEvent> listEvents() {
        return repository.findAllByOrderByReceivedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<LedgerEvent> listEventsPage(int page, int size, MoneyEventType type, String currency) {
        if (page < 0) {
            throw new InvalidMoneyEventException("Page must be non-negative");
        }
        if (size <= 0 || size > 200) {
            throw new InvalidMoneyEventException("Size must be between 1 and 200");
        }
        Pageable pageable = PageRequest.of(page, size);
        String normalizedCurrency = null;
        if (currency != null && !currency.isBlank()) {
            if (!MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
                throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
            }
            normalizedCurrency = currency.toUpperCase();
        }
        if (type != null && normalizedCurrency != null) {
            return repository.findByTypeAndCurrencyOrderByReceivedAtDesc(type, normalizedCurrency, pageable);
        }
        if (type != null) {
            return repository.findByTypeOrderByReceivedAtDesc(type, pageable);
        }
        if (normalizedCurrency != null) {
            return repository.findByCurrencyOrderByReceivedAtDesc(normalizedCurrency, pageable);
        }
        return repository.findAllByOrderByReceivedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEvent> getEvent(String eventId) {
        return repository.findById(eventId);
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEvent> findEventByTimestamp(Instant receivedAt) {
        Instant normalized = receivedAt.truncatedTo(ChronoUnit.MICROS);
        List<LedgerEvent> ordered = repository.findAllByOrderByReceivedAtAsc();
        int index = binarySearchByTimestamp(ordered, normalized);
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.of(ordered.get(index));
    }

    @Transactional(readOnly = true)
    public List<LedgerEvent> topChargesByAmount(int limit) {
        if (limit <= 0) {
            throw new InvalidMoneyEventException("Limit must be positive");
        }
        List<LedgerEvent> charges = repository.findByTypeOrderByAmountMinorDesc(MoneyEventType.charge);
        PriorityQueue<LedgerEvent> topK = new PriorityQueue<>(Comparator.comparingLong(LedgerEvent::getAmountMinor));
        for (LedgerEvent charge : charges) {
            topK.offer(charge);
            if (topK.size() > limit) {
                topK.poll();
            }
        }
        List<LedgerEvent> result = new ArrayList<>(topK);
        result.sort(Comparator.comparingLong(LedgerEvent::getAmountMinor).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public long balanceMinorForCurrency(String currency) {
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
        }
        return repository.balanceMinorByCurrency(currency.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<LedgerEvent> payoutSourceCharges(String payoutEventId) {
        return settlementGraphService.walkPayoutToSourceCharges(payoutEventId);
    }

    @Transactional
    public Optional<LedgerEvent> replay(String eventId, Optional<String> idempotencyKey) {
        if (idempotencyKey.isPresent()) {
            String key = normalizeIdempotencyKey(idempotencyKey.get());
            Optional<ReplayIdempotency> existing = replayIdempotencyRepository.findById(key);
            if (existing.isPresent()) {
                if (!existing.get().getEventId().equals(eventId)) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key was already used for a different event replay");
                }
                return repository.findById(eventId);
            }
            return applyReplay(eventId, key);
        }
        return applyReplay(eventId, null);
    }

    private Optional<LedgerEvent> applyReplay(String eventId, String idempotencyKey) {
        return repository.findById(eventId).map(event -> {
            event.recordReplay();
            replayUndoStack.push(eventId);
            LedgerEvent saved = repository.save(event);
            if (idempotencyKey != null) {
                replayIdempotencyRepository.save(
                        new ReplayIdempotency(idempotencyKey, eventId, Instant.now()));
            }
            return saved;
        });
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidMoneyEventException("Idempotency-Key must not be blank");
        }
        if (key.length() > 256) {
            throw new InvalidMoneyEventException("Idempotency-Key must be at most 256 characters");
        }
        return key.trim();
    }

    @Transactional
    public Optional<LedgerEvent> undoLastReplay() {
        if (replayUndoStack.isEmpty()) {
            return Optional.empty();
        }
        String eventId = replayUndoStack.pop();
        return repository.findById(eventId).map(event -> {
            event.undoLastReplay();
            return repository.save(event);
        });
    }

    @Transactional
    public Optional<LedgerEvent> reverse(String eventId) {
        return repository.findById(eventId).map(original -> {
            if (EventStateFlags.isReversed(original.getStateFlags())) {
                throw new InvalidMoneyEventException("Event is already reversed");
            }
            if (original.isReversal()) {
                throw new InvalidMoneyEventException("Cannot reverse a reversal entry");
            }
            String reversalId = eventId + "_reversal";
            if (reversalId.length() > 128 || !MoneyEventValidation.EVENT_ID.matcher(reversalId).matches()) {
                throw new InvalidMoneyEventException("Unable to derive a reversal event id for " + eventId);
            }
            if (repository.existsById(reversalId)) {
                throw new InvalidMoneyEventException("Reversal already exists for event " + eventId);
            }

            Instant reversalAt = periodCloseService.effectiveReversalTimestamp(
                    original.getReceivedAt(), Instant.now());
            periodCloseService.assertOpenFor(reversalAt);

            long reversalDebit = -original.getDebitMinor();
            long reversalCredit = -original.getCreditMinor();
            DoubleEntryResolver.validateBalanced(new DoubleEntryResolver.DoubleEntrySides(reversalDebit, reversalCredit));

            String rawPayload = buildReversalPayload(reversalId, original);
            LedgerEvent reversal = new LedgerEvent(
                    reversalId,
                    original.getType(),
                    original.getAmountMinor(),
                    reversalDebit,
                    reversalCredit,
                    original.getCurrency(),
                    rawPayload,
                    reversalAt,
                    original.getRefundChargeId(),
                    original.getPayoutChargeIds(),
                    eventId);

            original.markReversed();
            repository.save(original);
            repository.saveAndFlush(reversal);
            return reversal;
        });
    }

    private String buildReversalPayload(String reversalId, LedgerEvent original) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "id",
                            reversalId,
                            "type",
                            original.getType().name(),
                            "amount",
                            original.getAmountMinor(),
                            "currency",
                            original.getCurrency().toLowerCase(),
                            "reversesEventId",
                            original.getEventId()));
        } catch (JsonProcessingException e) {
            throw new InvalidMoneyEventException("Unable to serialize reversal payload", e);
        }
    }

    private MoneyEventPayload parsePayload(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, MoneyEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new InvalidMoneyEventException("Payload is not valid JSON for a money event", e);
        }
    }

    private void validatePayload(MoneyEventPayload payload) {
        if (payload.id() == null || !MoneyEventValidation.EVENT_ID.matcher(payload.id()).matches()) {
            throw new InvalidMoneyEventException("Event id must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
        }
        if (payload.type() == null) {
            throw new InvalidMoneyEventException("Event type is required (charge, refund, payout)");
        }
        if (!isSupportedType(payload.type())) {
            throw new InvalidMoneyEventException("Unsupported event type: " + payload.type());
        }
        if (payload.amount() < 0) {
            throw new InvalidMoneyEventException("Amount must be non-negative minor units");
        }
        if (payload.currency() == null || !MoneyEventValidation.CURRENCY.matcher(payload.currency()).matches()) {
            throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
        }
        if (payload.type() == MoneyEventType.refund) {
            if (payload.chargeId() == null || !MoneyEventValidation.EVENT_ID.matcher(payload.chargeId()).matches()) {
                throw new InvalidMoneyEventException("Refund must include a valid chargeId");
            }
            if (payload.id().equals(payload.chargeId())) {
                throw new SettlementGraphException("Settlement reference cannot point to the same event id");
            }
        }
        if (payload.type() == MoneyEventType.payout) {
            if (payload.chargeIds() == null || payload.chargeIds().isEmpty()) {
                throw new InvalidMoneyEventException("Payout must include chargeIds");
            }
            for (String chargeId : payload.chargeIds()) {
                if (!MoneyEventValidation.EVENT_ID.matcher(chargeId).matches()) {
                    throw new InvalidMoneyEventException("Payout chargeIds must be valid event ids");
                }
            }
        }
    }

    private List<String> settlementTargets(MoneyEventPayload payload) {
        if (payload.type() == MoneyEventType.refund) {
            return List.of(payload.chargeId());
        }
        if (payload.type() == MoneyEventType.payout) {
            return payload.chargeIds();
        }
        return List.of();
    }

    private boolean isSupportedType(MoneyEventType type) {
        return type == MoneyEventType.charge
                || type == MoneyEventType.refund
                || type == MoneyEventType.payout;
    }

    private static int binarySearchByTimestamp(List<LedgerEvent> ordered, Instant target) {
        int low = 0;
        int high = ordered.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Instant midAt = ordered.get(mid).getReceivedAt().truncatedTo(ChronoUnit.MICROS);
            int cmp = midAt.compareTo(target);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    public enum IngestStatus {
        CREATED,
        ALREADY_EXISTS
    }

    public record IngestResult(IngestStatus status, LedgerEvent event) {}
}
