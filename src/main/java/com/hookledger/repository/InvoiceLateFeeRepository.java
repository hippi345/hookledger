package com.hookledger.repository;

import com.hookledger.domain.InvoiceLateFee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLateFeeRepository extends JpaRepository<InvoiceLateFee, String> {

    boolean existsByInvoiceInvoiceId(String invoiceId);
}
