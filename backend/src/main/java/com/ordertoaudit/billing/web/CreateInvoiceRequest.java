package com.ordertoaudit.billing.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Body of POST /api/invoices. */
public record CreateInvoiceRequest(@NotNull Long orderId, @NotNull LocalDate invoiceDate) {
}
