package com.hookledger.repository;

import com.hookledger.domain.ReplayIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplayIdempotencyRepository extends JpaRepository<ReplayIdempotency, String> {}
