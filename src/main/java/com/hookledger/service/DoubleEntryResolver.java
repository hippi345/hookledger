package com.hookledger.service;

import com.hookledger.domain.MoneyEventType;
import com.hookledger.webhook.MoneyEventPayload;

final class DoubleEntryResolver {

    private DoubleEntryResolver() {}

    static DoubleEntrySides resolve(MoneyEventPayload payload) {
        Long debit = payload.debitMinor();
        Long credit = payload.creditMinor();
        if (debit != null || credit != null) {
            if (debit == null || credit == null) {
                throw new InvalidMoneyEventException("debitMinor and creditMinor must both be provided");
            }
            return new DoubleEntrySides(debit, credit);
        }
        long amount = payload.amount();
        return switch (payload.type()) {
            case charge -> new DoubleEntrySides(amount, -amount);
            case refund, payout -> new DoubleEntrySides(-amount, amount);
        };
    }

    static void validateBalanced(DoubleEntrySides sides) {
        if (sides.debitMinor() + sides.creditMinor() != 0) {
            throw new InvalidMoneyEventException("Ledger sides must sum to zero in minor units");
        }
    }

    record DoubleEntrySides(long debitMinor, long creditMinor) {}
}
