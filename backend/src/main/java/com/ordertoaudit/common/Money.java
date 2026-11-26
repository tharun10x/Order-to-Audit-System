package com.ordertoaudit.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;

/**
 * Scales and rounding used everywhere (R4). BigDecimal only, HALF_UP always.
 * Database scales: quantity 4, money 2, rate / unit cost 4.
 */
public final class Money {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final int QTY_SCALE = 4;
    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 4;

    /** The factory is in Tamil Nadu: dates on documents mean dates in this zone. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private Money() {
    }

    public static BigDecimal qty(BigDecimal value) {
        return value.setScale(QTY_SCALE, ROUNDING);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal rate(BigDecimal value) {
        return value.setScale(RATE_SCALE, ROUNDING);
    }
}
