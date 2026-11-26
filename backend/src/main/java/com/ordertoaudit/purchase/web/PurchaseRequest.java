package com.ordertoaudit.purchase.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body of POST /api/purchases. Quantity and rate are in the unit named on the line, exactly as on the supplier bill. */
public record PurchaseRequest(
        @NotNull Long supplierId,
        @NotNull LocalDate purchaseDate,
        @NotBlank @Size(max = 50) String billNo,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String itemCode,
            @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal qty,
            @NotBlank String uomCode,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal rate) {
    }
}
