package com.ordertoaudit.billing.entity;

/** invoice.status. A posted invoice is only ever cancelled by a credit note (R10.6). */
public enum InvoiceStatus {
    POSTED,
    CANCELLED
}
