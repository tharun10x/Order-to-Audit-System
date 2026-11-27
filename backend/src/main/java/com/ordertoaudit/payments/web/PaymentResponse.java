package com.ordertoaudit.payments.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ordertoaudit.payments.entity.InvoicePaymentStatus;
import com.ordertoaudit.payments.entity.PaymentMode;
import com.ordertoaudit.payments.entity.PaymentStatus;

public record PaymentResponse(
        Long id,
        Long customerId,
        String customerName,
        LocalDate receivedOn,
        BigDecimal amount,
        PaymentMode mode,
        String reference,
        PaymentStatus status,
        List<Allocation> allocations) {

    /** outstanding and paymentStatus describe the invoice as it is now. Both are null if it has been cancelled. */
    public record Allocation(Long invoiceId, String invoiceNo, BigDecimal amount, BigDecimal outstanding,
                             InvoicePaymentStatus paymentStatus) {
    }
}
