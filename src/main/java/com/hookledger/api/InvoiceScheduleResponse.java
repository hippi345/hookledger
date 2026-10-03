package com.hookledger.api;

import com.hookledger.domain.InvoiceSchedule;
import com.hookledger.domain.InvoiceScheduleInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceScheduleResponse(
        String id,
        String customerName,
        String customerAddress,
        String currency,
        InvoiceScheduleInterval interval,
        LocalDate lastGeneratedDueDate,
        Instant createdAt,
        List<InvoiceScheduleLineItemResponse> lineItems) {

    public static InvoiceScheduleResponse from(InvoiceSchedule schedule) {
        return new InvoiceScheduleResponse(
                schedule.getScheduleId(),
                schedule.getCustomerName(),
                schedule.getCustomerAddress(),
                schedule.getCurrency(),
                schedule.getInterval(),
                schedule.getLastGeneratedDueDate(),
                schedule.getCreatedAt(),
                schedule.getLineItems().stream().map(InvoiceScheduleLineItemResponse::from).toList());
    }
}
