package com.hookledger.repository;

import com.hookledger.domain.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, String> {

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM InvoicePayment p WHERE p.invoice.invoiceId = :invoiceId")
    long sumAmountMinorByInvoiceId(@Param("invoiceId") String invoiceId);
}
