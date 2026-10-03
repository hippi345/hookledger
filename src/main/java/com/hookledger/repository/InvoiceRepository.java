package com.hookledger.repository;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.invoiceId = :invoiceId")
    Optional<Invoice> findByIdWithLineItems(@Param("invoiceId") String invoiceId);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.status = :status")
    List<Invoice> findAllByStatusWithLineItems(@Param("status") InvoiceStatus status);

    @Query(
            """
            SELECT i FROM Invoice i
            WHERE i.customerName = :customerName AND i.currency = :currency AND i.status <> com.hookledger.domain.InvoiceStatus.voided
            """)
    List<Invoice> findByCustomerNameAndCurrency(
            @Param("customerName") String customerName, @Param("currency") String currency);

    @Query(
            """
            SELECT DISTINCT i.customerName FROM Invoice i
            WHERE LOWER(i.customerName) LIKE LOWER(CONCAT(:prefix, '%'))
            ORDER BY i.customerName
            """)
    List<String> findDistinctCustomerNamesByPrefix(@Param("prefix") String prefix);

    @Query("SELECT DISTINCT i.customerName FROM Invoice i ORDER BY i.customerName")
    List<String> findDistinctCustomerNames();

    @Query(
            value = """
            SELECT i FROM Invoice i
            WHERE (:customerName IS NULL OR i.customerName = :customerName)
            AND (:invoiceStatus IS NULL OR i.status = :invoiceStatus)
            AND (:currency IS NULL OR i.currency = :currency)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Invoice i
            WHERE (:customerName IS NULL OR i.customerName = :customerName)
            AND (:invoiceStatus IS NULL OR i.status = :invoiceStatus)
            AND (:currency IS NULL OR i.currency = :currency)
            """)
    Page<Invoice> findFiltered(
            @Param("customerName") String customerName,
            @Param("invoiceStatus") InvoiceStatus invoiceStatus,
            @Param("currency") String currency,
            Pageable pageable);

    @Query(
            "SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.customerName = :customerName AND i.currency = :currency AND i.status = :status")
    List<Invoice> findOpenByCustomerNameAndCurrencyWithLineItems(
            @Param("customerName") String customerName,
            @Param("currency") String currency,
            @Param("status") InvoiceStatus status);
}
