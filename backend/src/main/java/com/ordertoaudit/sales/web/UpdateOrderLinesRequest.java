package com.ordertoaudit.sales.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of PUT /api/orders/{id}/lines: the complete new set of lines. qty in packets, rate per packet (R9.3). */
public record UpdateOrderLinesRequest(@NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String itemCode,
            @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal qty,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal rate) {
    }
}
