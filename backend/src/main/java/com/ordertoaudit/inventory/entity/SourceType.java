package com.ordertoaudit.inventory.entity;

/** stock_movement.source_type: the kind of document that caused the movement (R6.2). */
public enum SourceType {
    OPENING,
    PURCHASE,
    PRODUCTION_RUN,
    DISPATCH,
    CREDIT_NOTE,
    ADJUSTMENT
}
