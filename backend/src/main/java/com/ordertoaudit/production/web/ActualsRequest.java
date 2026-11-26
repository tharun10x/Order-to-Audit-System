package com.ordertoaudit.production.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of PUT /api/production-runs/{id}/actuals.
 * actualOutput is good packets. Each line gives what was actually issued (scrap included) and how much of it
 * was scrap, in the unit named on the line (oil can be 10500 ml).
 */
public record ActualsRequest(
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal actualOutput,
        @NotEmpty @Valid List<Line> lines) {

    /** scrapQty is optional and means 0 when left out. */
    public record Line(
            @NotBlank String itemCode,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal actualQty,
            @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal scrapQty,
            @NotBlank String uomCode) {
    }
}
