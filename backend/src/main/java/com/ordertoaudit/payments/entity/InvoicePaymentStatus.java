package com.ordertoaudit.payments.entity;

import java.math.BigDecimal;

/** How far an invoice has been paid. Always derived, never stored (R12.3). */
public enum InvoicePaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID;

    public static InvoicePaymentStatus of(BigDecimal paid, BigDecimal outstanding) {
        if (outstanding.signum() == 0) {
            return PAID;
        }
        return paid.signum() == 0 ? UNPAID : PARTIALLY_PAID;
    }
}
