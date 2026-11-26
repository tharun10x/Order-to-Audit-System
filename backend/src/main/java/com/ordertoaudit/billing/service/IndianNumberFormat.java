package com.ordertoaudit.billing.service;

import java.math.BigDecimal;

import com.ordertoaudit.common.Money;

/**
 * Money with Indian digit grouping: the last three digits, then groups of two (1,00,000.00).
 * java.text.DecimalFormat only supports one group size, so this is written by hand.
 */
public final class IndianNumberFormat {

    private IndianNumberFormat() {
    }

    public static String money(BigDecimal amount) {
        String plain = Money.money(amount.abs()).toPlainString();
        int point = plain.indexOf('.');
        String digits = plain.substring(0, point);

        StringBuilder grouped = new StringBuilder(digits);
        // The first comma goes before the last three digits, then one every two digits to the left
        for (int position = digits.length() - 3; position > 0; position -= 2) {
            grouped.insert(position, ',');
        }
        String sign = amount.signum() < 0 ? "-" : "";
        return sign + grouped + plain.substring(point);
    }
}
