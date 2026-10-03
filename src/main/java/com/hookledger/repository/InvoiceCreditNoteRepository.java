package com.hookledger.repository;

import com.hookledger.domain.InvoiceCreditNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceCreditNoteRepository extends JpaRepository<InvoiceCreditNote, String> {

    @Query("SELECT COALESCE(SUM(c.amountMinor), 0) FROM InvoiceCreditNote c WHERE c.invoice.invoiceId = :invoiceId")
    long sumAmountMinorByInvoiceId(@Param("invoiceId") String invoiceId);

    List<InvoiceCreditNote> findByInvoiceInvoiceIdOrderByCreatedAtAsc(String invoiceId);

    @Query(
            """
            SELECT c FROM InvoiceCreditNote c JOIN c.invoice i
            WHERE i.customerName = :customerName AND c.currency = :currency AND i.status <> com.hookledger.domain.InvoiceStatus.voided
            """)
    List<InvoiceCreditNote> findByCustomerNameAndCurrency(
            @Param("customerName") String customerName, @Param("currency") String currency);

    void deleteByInvoiceInvoiceId(String invoiceId);
}
