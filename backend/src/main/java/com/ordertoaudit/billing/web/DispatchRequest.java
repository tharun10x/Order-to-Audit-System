package com.ordertoaudit.billing.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/dispatches. The whole invoice is dispatched (R11.4). dispatchDate defaults to the business date. */
public record DispatchRequest(
        @NotNull Long invoiceId,
        LocalDate dispatchDate,
        @Size(max = 20) String vehicleNo) {
}
