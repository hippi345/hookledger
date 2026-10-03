package com.hookledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.LedgerEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FxConversionService {

    private final LedgerEventRepository repository;
    private final ObjectMapper objectMapper;
    private final PeriodCloseService periodCloseService;
    private final LedgerAuditService ledgerAuditService;

    public FxConversionService(
            LedgerEventRepository repository,
            ObjectMapper objectMapper,
            PeriodCloseService periodCloseService,
            LedgerAuditService ledgerAuditService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.periodCloseService = periodCloseService;
        this.ledgerAuditService = ledgerAuditService;
    }

    @Transactional
    public FxConversionResult convert(
            String id,
            long sourceAmountMinor,
            String sourceCurrency,
            String targetCurrency,
            String rateText) {
        validateId(id);
        if (sourceAmountMinor <= 0) {
            throw new InvalidMoneyEventException("sourceAmountMinor must be positive");
        }
        if (sourceCurrency == null || !MoneyEventValidation.CURRENCY.matcher(sourceCurrency).matches()) {
            throw new InvalidMoneyEventException("sourceCurrency must be a 3-letter ISO code");
        }
        if (targetCurrency == null || !MoneyEventValidation.CURRENCY.matcher(targetCurrency).matches()) {
            throw new InvalidMoneyEventException("targetCurrency must be a 3-letter ISO code");
        }
        String source = sourceCurrency.toUpperCase();
        String target = targetCurrency.toUpperCase();
        if (source.equals(target)) {
            throw new InvalidMoneyEventException("source and target currency must differ");
        }
        BigDecimal rate = parseRate(rateText);
        long convertedMinor = rate
                .multiply(BigDecimal.valueOf(sourceAmountMinor))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (convertedMinor <= 0) {
            throw new InvalidMoneyEventException("Converted amount must be positive");
        }

        String sourceEventId = id + "_fx_out";
        String targetEventId = id + "_fx_in";
        if (repository.existsById(sourceEventId) || repository.existsById(targetEventId)) {
            throw new InvalidMoneyEventException("FX conversion id already exists: " + id);
        }

        Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        periodCloseService.assertOpenFor(receivedAt);

        LedgerEvent sourceLeg = new LedgerEvent(
                sourceEventId,
                MoneyEventType.fx_conversion,
                sourceAmountMinor,
                sourceAmountMinor,
                -sourceAmountMinor,
                source,
                buildPayload(sourceEventId, id, sourceAmountMinor, source, target, rateText, convertedMinor, true),
                receivedAt,
                null,
                List.of(),
                null,
                null,
                rateText,
                convertedMinor,
                target,
                false);
        LedgerEvent targetLeg = new LedgerEvent(
                targetEventId,
                MoneyEventType.fx_conversion,
                convertedMinor,
                convertedMinor,
                -convertedMinor,
                target,
                buildPayload(targetEventId, id, sourceAmountMinor, source, target, rateText, convertedMinor, false),
                receivedAt,
                null,
                List.of(),
                null,
                null,
                rateText,
                convertedMinor,
                source,
                true);

        repository.saveAndFlush(sourceLeg);
        repository.saveAndFlush(targetLeg);
        ledgerAuditService.record(
                id,
                com.hookledger.domain.LedgerAuditAction.post,
                "fx_conversion source=" + sourceEventId + " target=" + targetEventId);

        return new FxConversionResult(id, rateText, sourceLeg, targetLeg);
    }

    private static void validateId(String id) {
        if (id == null || !MoneyEventValidation.EVENT_ID.matcher(id).matches()) {
            throw new InvalidMoneyEventException("id must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
        }
        String suffix = "_fx_out";
        if (id.length() + suffix.length() > 128) {
            throw new InvalidMoneyEventException("id is too long to derive FX leg event ids");
        }
    }

    private static BigDecimal parseRate(String rateText) {
        if (rateText == null || rateText.isBlank()) {
            throw new InvalidMoneyEventException("rate is required");
        }
        try {
            BigDecimal rate = new BigDecimal(rateText.trim());
            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidMoneyEventException("rate must be positive");
            }
            return rate;
        } catch (NumberFormatException ex) {
            throw new InvalidMoneyEventException("rate must be a decimal number", ex);
        }
    }

    private String buildPayload(
            String eventId,
            String conversionId,
            long sourceAmountMinor,
            String sourceCurrency,
            String targetCurrency,
            String rate,
            long convertedMinor,
            boolean outbound) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "id",
                            eventId,
                            "conversionId",
                            conversionId,
                            "type",
                            MoneyEventType.fx_conversion.name(),
                            "sourceAmountMinor",
                            sourceAmountMinor,
                            "sourceCurrency",
                            sourceCurrency.toLowerCase(),
                            "targetCurrency",
                            targetCurrency.toLowerCase(),
                            "rate",
                            rate,
                            "convertedAmountMinor",
                            convertedMinor,
                            "outbound",
                            outbound));
        } catch (JsonProcessingException e) {
            throw new InvalidMoneyEventException("Unable to serialize FX payload", e);
        }
    }

    public record FxConversionResult(String id, String rate, LedgerEvent sourceLeg, LedgerEvent targetLeg) {}
}
