package com.ordertoaudit.production.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of POST /api/production-runs. */
public record CreateRunRequest(
        @NotBlank String productCode,
        @NotNull LocalDate runDate,
        @NotNull @Positive @Digits(integer = 6, fraction = 4) BigDecimal batches) {
}
