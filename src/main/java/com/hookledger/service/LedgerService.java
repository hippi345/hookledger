package com.hookledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.webhook.MoneyEventPayload;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerEventRepository repository;
    private final ObjectMapper objectMapper;
    private final SettlementGraphService settlementGraphService;
    private final Deque<String> replayUndoStack = new ArrayDeque<>();

    public LedgerService(
            LedgerEventRepository repository,
            ObjectMapper objectMapper,
            SettlementGraphService settlementGraphService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.settlementGraphService = settlementGraphService;
    }

    @Transactional
    public IngestResult ingest(String rawPayload) {
        MoneyEventPayload payload = parsePayload(rawPayload);
        validatePayload(payload);
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

        LedgerEvent event = new LedgerEvent(
                payload.id(),
                payload.type(),
                payload.amount(),
                payload.currency().toUpperCase(),
                rawPayload,
                Instant.now(),
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
    public Optional<LedgerEvent> getEvent(String eventId) {
        return repository.findById(eventId);
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEvent> findEventByTimestamp(Instant receivedAt) {
        List<LedgerEvent> ordered = repository.findAllByOrderByReceivedAtAsc();
        int index = binarySearchByTimestamp(ordered, receivedAt);
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
    public Optional<LedgerEvent> replay(String eventId) {
        return repository.findById(eventId).map(event -> {
            event.recordReplay();
            replayUndoStack.push(eventId);
            return repository.save(event);
        });
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
            Instant midAt = ordered.get(mid).getReceivedAt();
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
