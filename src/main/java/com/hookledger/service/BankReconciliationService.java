package com.hookledger.service;

import com.hookledger.domain.BankLine;
import com.hookledger.domain.LedgerEvent;
import com.hookledger.repository.BankLineRepository;
import com.hookledger.repository.LedgerEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankReconciliationService {

    private final BankLineRepository bankLineRepository;
    private final LedgerEventRepository ledgerEventRepository;

    public BankReconciliationService(
            BankLineRepository bankLineRepository, LedgerEventRepository ledgerEventRepository) {
        this.bankLineRepository = bankLineRepository;
        this.ledgerEventRepository = ledgerEventRepository;
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
        return bankLineRepository.findByMatchedLedgerEventIdIsNullOrderByCreatedAtAsc();
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
}
