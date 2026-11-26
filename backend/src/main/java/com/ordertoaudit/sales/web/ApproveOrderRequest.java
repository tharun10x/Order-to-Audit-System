package com.ordertoaudit.sales.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Optional body of POST /api/orders/{id}/approve. A line that is not listed is approved in full. */
public record ApproveOrderRequest(@Valid List<Line> lines) {

    public record Line(
            @NotBlank String itemCode,
            @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal approvedQty) {
    }
}
