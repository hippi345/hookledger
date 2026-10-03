package com.hookledger.repository;

import com.hookledger.domain.InvoicePayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, String> {

    @Query(
            "SELECT COALESCE(SUM(p.amountMinor), 0) FROM InvoicePayment p WHERE p.invoice.invoiceId = :invoiceId AND p.refundedAt IS NULL")
    long sumAmountMinorByInvoiceId(@Param("invoiceId") String invoiceId);

    @Query(
            "SELECT p FROM InvoicePayment p WHERE p.paymentId = :paymentId AND p.invoice.invoiceId = :invoiceId")
    java.util.Optional<InvoicePayment> findByPaymentIdAndInvoiceId(
            @Param("paymentId") String paymentId, @Param("invoiceId") String invoiceId);

    long countByInvoiceInvoiceId(String invoiceId);

    List<InvoicePayment> findByInvoiceInvoiceIdOrderByCreatedAtAsc(String invoiceId);

    @Query(
            """
            SELECT p FROM InvoicePayment p JOIN p.invoice i
            WHERE i.customerName = :customerName AND p.currency = :currency AND i.status <> com.hookledger.domain.InvoiceStatus.voided
            """)
    List<InvoicePayment> findByCustomerNameAndCurrency(
            @Param("customerName") String customerName, @Param("currency") String currency);
}
