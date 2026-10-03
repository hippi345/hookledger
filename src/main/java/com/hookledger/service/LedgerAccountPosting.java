package com.hookledger.service;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import java.util.Set;

public final class LedgerAccountPosting {

    public static final Set<String> KNOWN_ACCOUNTS = Set.of("cash", "revenue", "payout_clearing");

    private LedgerAccountPosting() {}

    public record Posting(long debitMinor, long creditMinor) {}

    public record AccountPair(String debitAccount, String creditAccount) {}

    public static AccountPair accountsFor(MoneyEventType type) {
        return switch (type) {
            case charge -> new AccountPair("cash", "revenue");
            case refund -> new AccountPair("revenue", "cash");
            case payout -> new AccountPair("payout_clearing", "cash");
            case fee -> new AccountPair("revenue", "cash");
            case fx_conversion -> new AccountPair("payout_clearing", "cash");
        };
    }

    public static AccountPair accountsForFx(LedgerEvent event) {
        if (event.isFxInbound()) {
            return new AccountPair("cash", "payout_clearing");
        }
        return new AccountPair("payout_clearing", "cash");
    }

    public static Posting postingForAccount(LedgerEvent event, String account) {
        long debitLeg = Math.abs(event.getDebitMinor());
        long creditLeg = Math.abs(event.getCreditMinor());
        AccountPair pair = event.getType() == MoneyEventType.fx_conversion
                ? accountsForFx(event)
                : accountsFor(event.getType());
        if (event.isReversal()) {
            pair = new AccountPair(pair.creditAccount(), pair.debitAccount());
        }
        if (pair.debitAccount().equals(account)) {
            return new Posting(debitLeg, 0);
        }
        if (pair.creditAccount().equals(account)) {
            return new Posting(0, creditLeg);
        }
        return new Posting(0, 0);
    }

    public static long netMinor(Posting posting) {
        return posting.debitMinor() - posting.creditMinor();
    }
}
