package com.hookledger.service;

import com.hookledger.domain.EventStateFlags;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.domain.PayoutSplitCharge;
import com.hookledger.repository.BankLineCombinationChargeRepository;
import com.hookledger.repository.BankLineRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PayoutSplitChargeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutSplitService {

    private final LedgerEventRepository ledgerEventRepository;
    private final PayoutSplitChargeRepository payoutSplitChargeRepository;
    private final BankLineRepository bankLineRepository;
    private final BankLineCombinationChargeRepository bankLineCombinationChargeRepository;

    public PayoutSplitService(
            LedgerEventRepository ledgerEventRepository,
            PayoutSplitChargeRepository payoutSplitChargeRepository,
            BankLineRepository bankLineRepository,
            BankLineCombinationChargeRepository bankLineCombinationChargeRepository) {
        this.ledgerEventRepository = ledgerEventRepository;
        this.payoutSplitChargeRepository = payoutSplitChargeRepository;
        this.bankLineRepository = bankLineRepository;
        this.bankLineCombinationChargeRepository = bankLineCombinationChargeRepository;
    }

    @Transactional
    public Optional<List<String>> splitPayout(String payoutEventId) {
        Optional<LedgerEvent> payoutOpt = ledgerEventRepository.findById(payoutEventId);
        if (payoutOpt.isEmpty()) {
            return Optional.empty();
        }
        LedgerEvent payout = payoutOpt.get();
        if (payout.getType() != MoneyEventType.payout) {
            throw new BankReconciliationException("event is not a payout");
        }
        if (payoutSplitChargeRepository.existsByPayoutEventId(payoutEventId)) {
            throw new BankReconciliationException("payout already has a charge split");
        }

        List<LedgerEvent> available = availableCharges(payout.getCurrency());
        Optional<List<String>> chargeIds =
                ChargeSubsetAlgorithms.fewestChargesSummingTo(available, payout.getAmountMinor());
        if (chargeIds.isEmpty()) {
            throw new BankReconciliationException("no charge combination sums to the payout amount");
        }

        for (String chargeId : chargeIds.get()) {
            payoutSplitChargeRepository.save(new PayoutSplitCharge(payoutEventId, chargeId));
        }
        return chargeIds;
    }

    @Transactional(readOnly = true)
    public Optional<List<String>> existingSplit(String payoutEventId) {
        if (!ledgerEventRepository.existsById(payoutEventId)) {
            return Optional.empty();
        }
        List<PayoutSplitCharge> links =
                payoutSplitChargeRepository.findByPayoutEventIdOrderByChargeEventIdAsc(payoutEventId);
        if (links.isEmpty()) {
            return Optional.of(List.of());
        }
        return Optional.of(links.stream().map(PayoutSplitCharge::getChargeEventId).toList());
    }

    private List<LedgerEvent> availableCharges(String currency) {
        List<LedgerEvent> result = new ArrayList<>();
        for (LedgerEvent event : ledgerEventRepository.findByTypeOrderByAmountMinorDesc(MoneyEventType.charge)) {
            if (!event.getCurrency().equals(currency)) {
                continue;
            }
            if (event.isReversal() || EventStateFlags.isReversed(event.getStateFlags())) {
                continue;
            }
            if (bankLineRepository.existsByMatchedLedgerEventId(event.getEventId())) {
                continue;
            }
            if (bankLineCombinationChargeRepository.existsByChargeEventId(event.getEventId())) {
                continue;
            }
            if (payoutSplitChargeRepository.existsByChargeEventId(event.getEventId())) {
                continue;
            }
            result.add(event);
        }
        result.sort(java.util.Comparator.comparing(LedgerEvent::getReceivedAt));
        return result;
    }
}
