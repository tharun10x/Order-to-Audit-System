package com.ordertoaudit.reports.model;

/**
 * How a column is stored and shown. Every exporter formats by this, so the services never format numbers.
 * PERCENT holds percentage points: -6.00 means -6 %, not -0.06.
 */
public enum ColumnType {
    TEXT,
    DATE,
    /** Quantity in the item's base unit, up to 4 decimals. */
    QTY,
    /** Rate or unit cost, 4 decimals. */
    RATE,
    /** Rupees, 2 decimals. */
    MONEY,
    PERCENT,
    /** A whole number such as a count of orders. */
    COUNT;

    public boolean isNumeric() {
        return this != TEXT && this != DATE;
    }
}
