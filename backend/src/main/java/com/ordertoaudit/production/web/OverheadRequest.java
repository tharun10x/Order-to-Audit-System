package com.ordertoaudit.production.web;

import java.math.BigDecimal;

import com.ordertoaudit.production.entity.OverheadType;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/production-runs/{id}/overheads. */
public record OverheadRequest(
        @NotNull OverheadType type,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @Size(max = 200) String note) {
}
