package com.hookledger.api;

import java.util.List;

public record PayoutSplitResponse(String payoutId, List<String> chargeIds) {}
