package com.hookledger.repository;

import com.hookledger.domain.Invoice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.invoiceId = :invoiceId")
    Optional<Invoice> findByIdWithLineItems(@Param("invoiceId") String invoiceId);
}
