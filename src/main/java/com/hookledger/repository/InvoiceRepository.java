package com.hookledger.repository;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.invoiceId = :invoiceId")
    Optional<Invoice> findByIdWithLineItems(@Param("invoiceId") String invoiceId);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.status = :status")
    List<Invoice> findAllByStatusWithLineItems(@Param("status") InvoiceStatus status);

    List<Invoice> findByCustomerNameAndCurrency(String customerName, String currency);

    @Query(
            "SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.customerName = :customerName AND i.currency = :currency AND i.status = :status")
    List<Invoice> findOpenByCustomerNameAndCurrencyWithLineItems(
            @Param("customerName") String customerName,
            @Param("currency") String currency,
            @Param("status") InvoiceStatus status);
}
