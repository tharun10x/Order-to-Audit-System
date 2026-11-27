package com.ordertoaudit.payments.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ordertoaudit.payments.entity.PaymentMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/payments. The allocations must add up to the amount (R12.6). receivedOn defaults to the business date. */
public record PaymentRequest(
        @NotNull Long customerId,
        LocalDate receivedOn,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull PaymentMode mode,
        @Size(max = 50) String reference,
        @NotEmpty @Valid List<Allocation> allocations) {

    public record Allocation(
            @NotNull Long invoiceId,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }
}
