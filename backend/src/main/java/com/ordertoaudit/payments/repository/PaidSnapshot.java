package com.ordertoaudit.payments.repository;

import java.math.BigDecimal;

/** Paid and outstanding of one invoice right now, read straight from the view. */
public interface PaidSnapshot {

    BigDecimal getPaid();

    BigDecimal getOutstanding();
}
