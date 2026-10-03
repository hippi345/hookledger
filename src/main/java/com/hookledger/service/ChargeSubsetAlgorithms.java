package com.hookledger.service;

import com.hookledger.domain.LedgerEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Subset-sum helpers for bank combination (backtracking) and payout split (dynamic programming).
 */
public final class ChargeSubsetAlgorithms {

    private ChargeSubsetAlgorithms() {}

    /** First charge subset that sums to {@code target} (backtracking, deterministic charge order). */
    public static Optional<List<String>> firstCombinationSummingTo(List<LedgerEvent> orderedCharges, long target) {
        List<String> picked = new ArrayList<>();
        if (backtrack(orderedCharges, 0, target, picked)) {
            return Optional.of(List.copyOf(picked));
        }
        return Optional.empty();
    }

    private static boolean backtrack(
            List<LedgerEvent> charges, int index, long remaining, List<String> picked) {
        if (remaining == 0) {
            return true;
        }
        if (remaining < 0 || index >= charges.size()) {
            return false;
        }
        LedgerEvent charge = charges.get(index);
        long amount = charge.getAmountMinor();
        picked.add(charge.getEventId());
        if (backtrack(charges, index + 1, remaining - amount, picked)) {
            return true;
        }
        picked.remove(picked.size() - 1);
        return backtrack(charges, index + 1, remaining, picked);
    }

    /** Fewest charges that sum to {@code target} (0/1 knapsack-style DP). */
    public static Optional<List<String>> fewestChargesSummingTo(List<LedgerEvent> orderedCharges, long target) {
        if (target < 0) {
            return Optional.empty();
        }
        Map<Long, PathNode> dp = new HashMap<>();
        dp.put(0L, new PathNode(0, -1, -1L));

        for (int chargeIndex = 0; chargeIndex < orderedCharges.size(); chargeIndex++) {
            long amount = orderedCharges.get(chargeIndex).getAmountMinor();
            if (amount <= 0 || amount > target) {
                continue;
            }
            List<Map.Entry<Long, PathNode>> snapshot = new ArrayList<>(dp.entrySet());
            for (Map.Entry<Long, PathNode> entry : snapshot) {
                long newSum = entry.getKey() + amount;
                if (newSum > target) {
                    continue;
                }
                int newCount = entry.getValue().count + 1;
                PathNode existing = dp.get(newSum);
                if (existing == null || newCount < existing.count) {
                    dp.put(newSum, new PathNode(newCount, chargeIndex, entry.getKey()));
                }
            }
        }

        PathNode atTarget = dp.get(target);
        if (atTarget == null || atTarget.count == 0 && target != 0) {
            return Optional.empty();
        }
        if (target == 0) {
            return Optional.of(List.of());
        }

        List<String> ids = new ArrayList<>();
        long sum = target;
        while (sum > 0) {
            PathNode node = dp.get(sum);
            if (node == null || node.chargeIndex < 0) {
                return Optional.empty();
            }
            ids.add(orderedCharges.get(node.chargeIndex).getEventId());
            sum = node.prevSum;
        }
        return Optional.of(List.copyOf(ids));
    }

    private record PathNode(int count, int chargeIndex, long prevSum) {}
}
