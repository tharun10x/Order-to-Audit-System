package com.ordertoaudit.masters.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of POST /api/boms: a new, inactive recipe version for one batch (R5.6, R5.8). outputQty is the expected
 * packets per batch. copyIngredientsFrom (a product code, optional) first copies the ingredient lines of that
 * product's active recipe (R5.10); lines then add the rest. Each line's qty is in its uomCode (the item's base
 * unit when left out) and is stored converted to the base unit (R5.9).
 */
public record BomRequest(
        @NotBlank String productCode,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal outputQty,
        String copyIngredientsFrom,
        @NotNull @Valid List<Line> lines) {

    public record Line(
            @NotBlank String itemCode,
            @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal qty,
            String uomCode) {
    }
}
