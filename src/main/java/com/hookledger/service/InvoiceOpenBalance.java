package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.repository.InvoiceCreditNoteRepository;
import com.hookledger.repository.InvoicePaymentRepository;

final class InvoiceOpenBalance {

    private InvoiceOpenBalance() {}

    static long openAmountMinor(
            Invoice invoice,
            InvoiceCreditNoteRepository creditNoteRepository,
            InvoicePaymentRepository paymentRepository) {
        long credits = creditNoteRepository.sumAmountMinorByInvoiceId(invoice.getInvoiceId());
        long payments = paymentRepository.sumAmountMinorByInvoiceId(invoice.getInvoiceId());
        return invoice.getTotalAmountMinor() - credits - payments;
    }
}
