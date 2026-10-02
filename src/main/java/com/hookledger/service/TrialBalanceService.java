package com.hookledger.service;

import com.hookledger.domain.LedgerEvent;
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
        LedgerAccountPosting.Posting cashSide = LedgerAccountPosting.postingForAccount(event, "cash");
        LedgerAccountPosting.Posting revenueSide = LedgerAccountPosting.postingForAccount(event, "revenue");
        LedgerAccountPosting.Posting clearingSide =
                LedgerAccountPosting.postingForAccount(event, "payout_clearing");
        addPosting(totals, event.getCurrency(), "cash", cashSide);
        addPosting(totals, event.getCurrency(), "revenue", revenueSide);
        addPosting(totals, event.getCurrency(), "payout_clearing", clearingSide);
    }

    private static void addPosting(
            Map<AccountKey, MutableLine> totals, String currency, String account, LedgerAccountPosting.Posting posting) {
        if (posting.debitMinor() > 0) {
            totals.computeIfAbsent(new AccountKey(currency, account), k -> new MutableLine()).debitMinor +=
                    posting.debitMinor();
        }
        if (posting.creditMinor() > 0) {
            totals.computeIfAbsent(new AccountKey(currency, account), k -> new MutableLine()).creditMinor +=
                    posting.creditMinor();
        }
    }

    private record AccountKey(String currency, String account) {}

    private static final class MutableLine {
        long debitMinor;
        long creditMinor;
    }

    public record TrialBalanceLine(String currency, String account, long debitMinor, long creditMinor) {}

    public record TrialBalance(
            List<TrialBalanceLine> lines, long totalDebitMinor, long totalCreditMinor, long netMinor, boolean balanced) {}
}
