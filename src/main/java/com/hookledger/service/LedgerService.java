package com.hookledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.webhook.MoneyEventPayload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerEventRepository repository;
    private final ObjectMapper objectMapper;

    public LedgerService(LedgerEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResult ingest(String rawPayload) {
        MoneyEventPayload payload = parsePayload(rawPayload);
        validatePayload(payload);

        LedgerEvent event = new LedgerEvent(
                payload.id(),
                payload.type(),
                payload.amount(),
                payload.currency().toUpperCase(),
                rawPayload,
                Instant.now());

        try {
            repository.saveAndFlush(event);
            return new IngestResult(IngestStatus.CREATED, event);
        } catch (DataIntegrityViolationException ex) {
            Optional<LedgerEvent> existing = repository.findById(payload.id());
            return existing
                    .map(e -> new IngestResult(IngestStatus.ALREADY_EXISTS, e))
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public List<LedgerEvent> listEvents() {
        return repository.findAllByOrderByReceivedAtDesc();
    }

    @Transactional
    public Optional<LedgerEvent> replay(String eventId) {
        return repository.findById(eventId).map(event -> {
            event.incrementReplayCount();
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
        if (payload.id() == null || payload.id().isBlank()) {
            throw new InvalidMoneyEventException("Event id is required");
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
        if (payload.currency() == null || !payload.currency().matches("[A-Za-z]{3}")) {
            throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
        }
    }

    private boolean isSupportedType(MoneyEventType type) {
        return type == MoneyEventType.charge
                || type == MoneyEventType.refund
                || type == MoneyEventType.payout;
    }

    public enum IngestStatus {
        CREATED,
        ALREADY_EXISTS
    }

    public record IngestResult(IngestStatus status, LedgerEvent event) {
    }
}
