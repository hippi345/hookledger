package com.hookledger.repository;

import com.hookledger.domain.LedgerEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    List<LedgerEvent> findAllByOrderByReceivedAtDesc();
}
