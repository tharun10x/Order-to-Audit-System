package com.ordertoaudit.billing.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/invoices/{id}/cancel. noteDate is the credit note date; today if left out. */
public record CancelInvoiceRequest(@NotBlank @Size(max = 500) String reason, LocalDate noteDate) {
}
