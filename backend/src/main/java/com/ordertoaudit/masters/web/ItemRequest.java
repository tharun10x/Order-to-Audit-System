package com.ordertoaudit.masters.web;

import java.math.BigDecimal;

import com.ordertoaudit.masters.entity.ItemKind;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/items and PUT /api/items/{code}. code and kind are fixed once created (M3): on an edit leave
 * them out or send the same values. The finished-goods fields (R5.1) are required for FG and must be empty
 * otherwise (M9). packetsPerCarton is stored as the item's ctn conversion; leaving it out on FG removes it.
 */
public record ItemRequest(
        @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        ItemKind kind,
        @NotBlank String baseUnit,
        @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal reorderLevel,
        String hsnCode,
        @PositiveOrZero @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal gstRate,
        @Positive @Digits(integer = 12, fraction = 2) BigDecimal sellingPrice,
        @Positive @Digits(integer = 10, fraction = 4) BigDecimal packWeightKg,
        @Positive @Max(10000) Integer packetsPerCarton) {
}
