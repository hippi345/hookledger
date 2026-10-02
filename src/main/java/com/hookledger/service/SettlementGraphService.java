package com.hookledger.service;

import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.LedgerEventRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SettlementGraphService {

    private final LedgerEventRepository repository;

    public SettlementGraphService(LedgerEventRepository repository) {
        this.repository = repository;
    }

    public void validateReferencesAcyclic(String newEventId, List<String> directTargets) {
        for (String target : directTargets) {
            if (newEventId.equals(target)) {
                throw new SettlementGraphException("Settlement reference cannot point to the same event id");
            }
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(directTargets);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (newEventId.equals(current)) {
                throw new SettlementGraphException("Settlement reference graph contains a cycle");
            }

            LedgerEvent currentEvent = repository
                    .findById(current)
                    .orElseThrow(() -> new InvalidMoneyEventException("Referenced event not found: " + current));

            for (String next : currentEvent.outgoingSettlementTargets()) {
                if (newEventId.equals(next)) {
                    throw new SettlementGraphException("Settlement reference graph contains a cycle");
                }
                queue.addLast(next);
            }

            for (LedgerEvent referrer : repository.findReferencingEventId(current)) {
                if (newEventId.equals(referrer.getEventId())) {
                    throw new SettlementGraphException("Settlement reference graph contains a cycle");
                }
                for (String next : referrer.outgoingSettlementTargets()) {
                    if (newEventId.equals(next)) {
                        throw new SettlementGraphException("Settlement reference graph contains a cycle");
                    }
                    queue.addLast(next);
                }
            }
        }
    }

    public void validateReferencedCharges(String newEventId, MoneyEventType type, List<String> directTargets) {
        if (type == MoneyEventType.charge) {
            if (!directTargets.isEmpty()) {
                throw new InvalidMoneyEventException("Charge events must not include settlement references");
            }
            return;
        }
        if (directTargets.isEmpty()) {
            throw new InvalidMoneyEventException("Refund and payout events must reference at least one charge");
        }
        for (String targetId : directTargets) {
            LedgerEvent target = repository
                    .findById(targetId)
                    .orElseThrow(() -> new InvalidMoneyEventException("Referenced charge not found: " + targetId));
            if (target.getType() != MoneyEventType.charge) {
                throw new InvalidMoneyEventException("Settlement reference must point to a charge event: " + targetId);
            }
            if (newEventId.equals(targetId)) {
                throw new SettlementGraphException("Settlement reference cannot point to the same event id");
            }
        }
    }

    public List<LedgerEvent> walkPayoutToSourceCharges(String payoutEventId) {
        LedgerEvent payout = repository
                .findById(payoutEventId)
                .orElseThrow(() -> new InvalidMoneyEventException("Payout event not found: " + payoutEventId));
        if (payout.getType() != MoneyEventType.payout) {
            throw new InvalidMoneyEventException("Event is not a payout: " + payoutEventId);
        }

        List<LedgerEvent> charges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(payout.outgoingSettlementTargets());
        while (!queue.isEmpty()) {
            String chargeId = queue.removeFirst();
            if (!seen.add(chargeId)) {
                continue;
            }
            Optional<LedgerEvent> charge = repository.findById(chargeId);
            if (charge.isEmpty() || charge.get().getType() != MoneyEventType.charge) {
                throw new InvalidMoneyEventException("Payout settlement chain is broken at: " + chargeId);
            }
            charges.add(charge.get());
        }
        return charges;
    }
}
