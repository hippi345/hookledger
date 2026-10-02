package com.hookledger.api;

import java.time.Instant;

public record PeriodCloseResponse(Instant lockedThrough) {}
