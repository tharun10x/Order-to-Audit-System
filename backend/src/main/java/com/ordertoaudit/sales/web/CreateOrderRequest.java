package com.ordertoaudit.sales.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of POST /api/orders. qty is in the unit named on the line (pkt or ctn, R3.5). */
public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotNull UUID clientUuid,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String itemCode,
            @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal qty,
            @NotBlank String uomCode) {
    }
}
