package com.ordertoaudit.billing.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Body of POST /api/invoices. invoiceDate is the business date (A17) when left out. */
public record CreateInvoiceRequest(@NotNull Long orderId, LocalDate invoiceDate) {
}
