package com.hookledger.service;

import com.hookledger.domain.BankLine;
import com.hookledger.domain.BankLineCombinationCharge;
import com.hookledger.domain.EventStateFlags;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.domain.MoneyEventType;
import com.hookledger.repository.BankLineCombinationChargeRepository;
import com.hookledger.repository.BankLineRepository;
import com.hookledger.repository.LedgerEventRepository;
import com.hookledger.repository.PayoutSplitChargeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankReconciliationService {

    private final BankLineRepository bankLineRepository;
    private final LedgerEventRepository ledgerEventRepository;
    private final BankLineCombinationChargeRepository combinationChargeRepository;
    private final PayoutSplitChargeRepository payoutSplitChargeRepository;

    public BankReconciliationService(
            BankLineRepository bankLineRepository,
            LedgerEventRepository ledgerEventRepository,
            BankLineCombinationChargeRepository combinationChargeRepository,
            PayoutSplitChargeRepository payoutSplitChargeRepository) {
        this.bankLineRepository = bankLineRepository;
        this.ledgerEventRepository = ledgerEventRepository;
        this.combinationChargeRepository = combinationChargeRepository;
        this.payoutSplitChargeRepository = payoutSplitChargeRepository;
    }

    @Transactional
    public BankLine postLine(long amountMinor, String currency, LocalDate lineDate) {
        if (amountMinor <= 0) {
            throw new BankReconciliationException("amount must be a positive minor-unit value");
        }
        if (currency == null || !MoneyEventValidation.CURRENCY.matcher(currency).matches()) {
            throw new BankReconciliationException("currency must be a 3-letter ISO code");
        }
        if (lineDate == null) {
            throw new BankReconciliationException("date is required");
        }
        BankLine line = new BankLine(amountMinor, currency.toUpperCase(), lineDate, Instant.now());
        return bankLineRepository.save(line);
    }

    @Transactional(readOnly = true)
    public List<BankLine> listUnmatched() {
        return bankLineRepository.findByMatchedAtIsNullOrderByCreatedAtAsc();
    }

    @Transactional
    public Optional<BankLine> match(String bankLineId, String ledgerEventId) {
        Optional<BankLine> bankLineOpt = bankLineRepository.findById(bankLineId);
        if (bankLineOpt.isEmpty()) {
            return Optional.empty();
        }
        BankLine bankLine = bankLineOpt.get();
        if (bankLine.isMatched()) {
            throw new BankReconciliationException("bank line is already matched");
        }
        Optional<LedgerEvent> ledgerOpt = ledgerEventRepository.findById(ledgerEventId);
        if (ledgerOpt.isEmpty()) {
            return Optional.empty();
        }
        LedgerEvent ledgerEvent = ledgerOpt.get();
        if (bankLine.getAmountMinor() != ledgerEvent.getAmountMinor()) {
            throw new BankReconciliationException(
                    "ledger entry amount does not match bank line amount");
        }
        if (!bankLine.getCurrency().equals(ledgerEvent.getCurrency())) {
            throw new BankReconciliationException(
                    "ledger entry currency does not match bank line currency");
        }
        if (bankLineRepository.existsByMatchedLedgerEventId(ledgerEventId)) {
            throw new BankReconciliationException("ledger entry is already matched to a bank line");
        }
        bankLine.matchTo(ledgerEventId, Instant.now());
        return Optional.of(bankLineRepository.save(bankLine));
    }

    @Transactional
    public Optional<BankLine> matchGreedy(String bankLineId) {
        Optional<BankLine> bankLineOpt = bankLineRepository.findById(bankLineId);
        if (bankLineOpt.isEmpty()) {
            return Optional.empty();
        }
        BankLine bankLine = bankLineOpt.get();
        if (bankLine.isMatched()) {
            throw new BankReconciliationException("bank line is already matched");
        }
        Optional<LedgerEvent> ledgerOpt = oldestUnmatchedLedgerEntry(bankLine);
        if (ledgerOpt.isEmpty()) {
            throw new BankReconciliationException("no unmatched ledger entry matches this bank line");
        }
        return match(bankLineId, ledgerOpt.get().getEventId());
    }

    @Transactional(readOnly = true)
    public Optional<String> suggestMatch(String bankLineId) {
        Optional<BankLine> bankLineOpt = bankLineRepository.findById(bankLineId);
        if (bankLineOpt.isEmpty()) {
            return Optional.empty();
        }
        BankLine bankLine = bankLineOpt.get();
        if (bankLine.isMatched()) {
            throw new BankReconciliationException("bank line is already matched");
        }
        return oldestUnmatchedLedgerEntry(bankLine).map(LedgerEvent::getEventId);
    }

    @Transactional
    public Optional<CombinationMatchResult> matchCombination(String bankLineId) {
        Optional<BankLine> bankLineOpt = bankLineRepository.findById(bankLineId);
        if (bankLineOpt.isEmpty()) {
            return Optional.empty();
        }
        BankLine bankLine = bankLineOpt.get();
        if (bankLine.isMatched()) {
            throw new BankReconciliationException("bank line is already matched");
        }

        List<LedgerEvent> charges = availableChargesForCombination(bankLine.getCurrency());
        Optional<List<String>> combination = ChargeSubsetAlgorithms.firstCombinationSummingTo(
                charges, bankLine.getAmountMinor());
        if (combination.isEmpty()) {
            throw new BankReconciliationException("no charge combination sums to the bank line amount");
        }

        Instant matchedAt = Instant.now();
        for (String chargeId : combination.get()) {
            combinationChargeRepository.save(new BankLineCombinationCharge(bankLineId, chargeId));
        }
        bankLine.markCombinationMatched(matchedAt);
        bankLineRepository.save(bankLine);
        return Optional.of(new CombinationMatchResult(bankLine, combination.get()));
    }

    private Optional<LedgerEvent> oldestUnmatchedLedgerEntry(BankLine bankLine) {
        return ledgerEventRepository.findAllByOrderByReceivedAtAsc().stream()
                .filter(event -> event.getAmountMinor() == bankLine.getAmountMinor())
                .filter(event -> bankLine.getCurrency().equals(event.getCurrency()))
                .filter(event -> !bankLineRepository.existsByMatchedLedgerEventId(event.getEventId()))
                .findFirst();
    }

    private List<LedgerEvent> availableChargesForCombination(String currency) {
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
            if (combinationChargeRepository.existsByChargeEventId(event.getEventId())) {
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

    public record CombinationMatchResult(BankLine bankLine, List<String> matchedChargeIds) {}
}
