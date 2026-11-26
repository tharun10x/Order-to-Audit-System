package com.ordertoaudit.purchase.entity;

/** purchase.status: a posted purchase is never edited. A wrong one is reversed (R7.6). */
public enum PurchaseStatus {
    POSTED,
    REVERSED
}
