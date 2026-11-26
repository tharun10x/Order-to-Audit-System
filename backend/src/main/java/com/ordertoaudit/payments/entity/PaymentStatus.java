package com.ordertoaudit.payments.entity;

/** payment.status. Only POSTED payments count towards invoices (R12.5). */
public enum PaymentStatus {
    POSTED,
    REVERSED
}
