package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.repository.InvoiceCreditNoteRepository;

final class InvoiceOpenBalance {

    private InvoiceOpenBalance() {}

    static long openAmountMinor(Invoice invoice, InvoiceCreditNoteRepository creditNoteRepository) {
        long applied = creditNoteRepository.sumAmountMinorByInvoiceId(invoice.getInvoiceId());
        return invoice.getTotalAmountMinor() - applied;
    }
}
