package com.hookledger.repository;

import com.hookledger.domain.InvoiceCreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceCreditNoteRepository extends JpaRepository<InvoiceCreditNote, String> {

    @Query("SELECT COALESCE(SUM(c.amountMinor), 0) FROM InvoiceCreditNote c WHERE c.invoice.invoiceId = :invoiceId")
    long sumAmountMinorByInvoiceId(@Param("invoiceId") String invoiceId);
}
