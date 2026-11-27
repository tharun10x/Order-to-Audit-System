package com.ordertoaudit.payments.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ordertoaudit.payments.entity.InvoicePaymentStatus;

/**
 * R12.4: what one customer owes as of asOf (the business date), worked out by OutstandingCalculator, the same
 * calculation as the outstanding report. Only invoices with something still to pay are listed. buckets holds the
 * four ageing buckets (0-30, 31-60, 61-90, 90+ days since invoice date) in that order, 0.00 when empty.
 */
public record CustomerOutstandingResponse(Long customerId, String customerName, LocalDate asOf,
                                          BigDecimal totalOutstanding, Map<String, BigDecimal> buckets,
                                          List<Invoice> invoices) {

    public record Invoice(Long invoiceId, String invoiceNo, LocalDate invoiceDate, BigDecimal total, BigDecimal paid,
                          BigDecimal outstanding, InvoicePaymentStatus paymentStatus, long days,
                          String ageingBucket) {
    }
}
