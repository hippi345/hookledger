package com.hookledger.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InvoiceStatus {
    open,
    paid,
    voided;

    @JsonValue
    public String toApiValue() {
        if (this == voided) {
            return "void";
        }
        return name();
    }

    @JsonCreator
    public static InvoiceStatus fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if ("void".equalsIgnoreCase(value.trim())) {
            return voided;
        }
        return valueOf(value.trim().toLowerCase());
    }
}
