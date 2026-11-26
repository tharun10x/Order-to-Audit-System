package com.ordertoaudit.common.error;

import java.math.BigDecimal;

/** R6.6: an outgoing movement would take stock below zero. Becomes HTTP 409. */
public class InsufficientStockException extends ConflictException {

    private final String itemCode;
    private final BigDecimal shortage;

    public InsufficientStockException(String itemCode, BigDecimal available, BigDecimal requested) {
        super("Insufficient stock for " + itemCode + ": available " + available.toPlainString()
                + ", requested " + requested.toPlainString()
                + ", short by " + requested.subtract(available).toPlainString());
        this.itemCode = itemCode;
        this.shortage = requested.subtract(available);
    }

    public String getItemCode() {
        return itemCode;
    }

    public BigDecimal getShortage() {
        return shortage;
    }
}
