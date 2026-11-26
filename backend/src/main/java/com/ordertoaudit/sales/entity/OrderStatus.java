package com.ordertoaudit.sales.entity;

/** sales_order.status (R9 status flow). */
public enum OrderStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    INVOICED,
    DISPATCHED,
    CANCELLED
}
