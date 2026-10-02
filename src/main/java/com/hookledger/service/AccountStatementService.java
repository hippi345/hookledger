package com.hookledger.service;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.repository.LedgerEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountStatementService {

    private final LedgerEventRepository repository;

    public AccountStatementService(LedgerEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AccountStatement build(String account, String currency, Instant fromInclusive, Instant toInclusive) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedCurrency = normalizeCurrency(currency);
        if (fromInclusive.isAfter(toInclusive)) {
            throw new InvalidMoneyEventException("from must not be after to");
        }

        long startingBalanceMinor = 0;
        List<LedgerEvent> before = repository.findByCurrencyAndReceivedAtLessThanOrderByReceivedAtAsc(
                normalizedCurrency, fromInclusive);
        for (LedgerEvent event : before) {
            startingBalanceMinor +=
                    LedgerAccountPosting.netMinor(LedgerAccountPosting.postingForAccount(event, normalizedAccount));
        }

        List<AccountStatementLine> lines = new ArrayList<>();
        long periodNet = 0;
        List<LedgerEvent> inRange = repository.findByCurrencyAndReceivedAtBetweenOrderByReceivedAtAsc(
                normalizedCurrency, fromInclusive, toInclusive);
        for (LedgerEvent event : inRange) {
            LedgerAccountPosting.Posting posting =
                    LedgerAccountPosting.postingForAccount(event, normalizedAccount);
            if (posting.debitMinor() == 0 && posting.creditMinor() == 0) {
                continue;
            }
            long net = LedgerAccountPosting.netMinor(posting);
            periodNet += net;
            lines.add(new AccountStatementLine(
                    event.getEventId(),
                    event.getType().name(),
                    event.getReceivedAt(),
                    posting.debitMinor(),
                    posting.creditMinor(),
                    normalizedCurrency));
        }

        long endingBalanceMinor = startingBalanceMinor + periodNet;
        return new AccountStatement(
                normalizedAccount,
                normalizedCurrency,
                fromInclusive,
                toInclusive,
                startingBalanceMinor,
                endingBalanceMinor,
                lines);
    }

    private static String normalizeAccount(String account) {
        if (account == null || account.isBlank()) {
            throw new InvalidMoneyEventException("Account is required");
        }
        String normalized = account.toLowerCase();
        if (!LedgerAccountPosting.KNOWN_ACCOUNTS.contains(normalized)) {
            throw new InvalidMoneyEventException("Unknown ledger account: " + account);
        }
        return normalized;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
        }
        return currency.toUpperCase();
    }

    public record AccountStatementLine(
            String eventId,
            String type,
            Instant receivedAt,
            long debitMinor,
            long creditMinor,
            String currency) {}

    public record AccountStatement(
            String account,
            String currency,
            Instant from,
            Instant to,
            long startingBalanceMinor,
            long endingBalanceMinor,
            List<AccountStatementLine> lines) {}
}
