package com.ordertoaudit.inventory.repository;

import java.math.BigDecimal;

/** The summed value of some movements for one item. */
public interface ItemValue {

    Long getItemId();

    BigDecimal getTotal();
}
