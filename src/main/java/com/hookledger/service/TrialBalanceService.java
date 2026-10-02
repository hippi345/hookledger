package com.hookledger.service;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.LedgerEventRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrialBalanceService {

    private final LedgerEventRepository repository;

    public TrialBalanceService(LedgerEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TrialBalance compute(String currencyFilter) {
        String normalizedFilter = null;
        if (currencyFilter != null && !currencyFilter.isBlank()) {
            if (!MoneyEventValidation.CURRENCY.matcher(currencyFilter).matches()) {
                throw new InvalidMoneyEventException("Currency must be a 3-letter ISO code");
            }
            normalizedFilter = currencyFilter.toUpperCase();
        }

        Map<AccountKey, MutableLine> totals = new HashMap<>();
        for (LedgerEvent event : repository.findAll()) {
            if (normalizedFilter != null && !event.getCurrency().equals(normalizedFilter)) {
                continue;
            }
            applyEvent(totals, event);
        }

        List<TrialBalanceLine> lines = new ArrayList<>();
        long totalDebit = 0;
        long totalCredit = 0;
        for (Map.Entry<AccountKey, MutableLine> entry : totals.entrySet()) {
            AccountKey key = entry.getKey();
            MutableLine line = entry.getValue();
            totalDebit += line.debitMinor;
            totalCredit += line.creditMinor;
            lines.add(new TrialBalanceLine(key.currency, key.account, line.debitMinor, line.creditMinor));
        }
        lines.sort(Comparator.comparing(TrialBalanceLine::currency).thenComparing(TrialBalanceLine::account));

        long netMinor = totalDebit - totalCredit;
        return new TrialBalance(lines, totalDebit, totalCredit, netMinor, netMinor == 0);
    }

    private static void applyEvent(Map<AccountKey, MutableLine> totals, LedgerEvent event) {
        long debitLeg = Math.abs(event.getDebitMinor());
        long creditLeg = Math.abs(event.getCreditMinor());
        String currency = event.getCurrency();

        AccountPair pair = accountsFor(event.getType());
        addDebit(totals, currency, pair.debitAccount, debitLeg);
        addCredit(totals, currency, pair.creditAccount, creditLeg);
    }

    private static AccountPair accountsFor(MoneyEventType type) {
        return switch (type) {
            case charge -> new AccountPair("cash", "revenue");
            case refund -> new AccountPair("revenue", "cash");
            case payout -> new AccountPair("payout_clearing", "cash");
        };
    }

    private static void addDebit(Map<AccountKey, MutableLine> totals, String currency, String account, long amount) {
        if (amount == 0) {
            return;
        }
        totals.computeIfAbsent(new AccountKey(currency, account), k -> new MutableLine()).debitMinor += amount;
    }

    private static void addCredit(Map<AccountKey, MutableLine> totals, String currency, String account, long amount) {
        if (amount == 0) {
            return;
        }
        totals.computeIfAbsent(new AccountKey(currency, account), k -> new MutableLine()).creditMinor += amount;
    }

    private record AccountKey(String currency, String account) {}

    private record AccountPair(String debitAccount, String creditAccount) {}

    private static final class MutableLine {
        long debitMinor;
        long creditMinor;
    }

    public record TrialBalanceLine(String currency, String account, long debitMinor, long creditMinor) {}

    public record TrialBalance(
            List<TrialBalanceLine> lines, long totalDebitMinor, long totalCreditMinor, long netMinor, boolean balanced) {}
}
