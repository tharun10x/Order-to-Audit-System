package com.ordertoaudit.payments.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ordertoaudit.payments.entity.InvoicePaymentStatus;

/** R12.4: what one customer owes. Only invoices with something still to pay are listed. */
public record CustomerOutstandingResponse(Long customerId, String customerName, BigDecimal totalOutstanding,
                                          List<Invoice> invoices) {

    public record Invoice(Long invoiceId, String invoiceNo, LocalDate invoiceDate, BigDecimal total, BigDecimal paid,
                          BigDecimal outstanding, InvoicePaymentStatus paymentStatus) {
    }
}
