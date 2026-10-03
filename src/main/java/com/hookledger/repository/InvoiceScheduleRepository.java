package com.hookledger.repository;

import com.hookledger.domain.InvoiceSchedule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceScheduleRepository extends JpaRepository<InvoiceSchedule, String> {

    @Query("SELECT s FROM InvoiceSchedule s LEFT JOIN FETCH s.lineItems WHERE s.scheduleId = :scheduleId")
    Optional<InvoiceSchedule> findByIdWithLineItems(@Param("scheduleId") String scheduleId);
}
