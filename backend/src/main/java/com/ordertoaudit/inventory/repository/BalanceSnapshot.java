package com.ordertoaudit.inventory.repository;

import java.math.BigDecimal;

/** Quantity and value of one item right now, read straight from the view (never from a cached entity). */
public interface BalanceSnapshot {

    BigDecimal getQtyOnHand();

    BigDecimal getStockValue();
}
