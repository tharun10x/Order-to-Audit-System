package com.ordertoaudit.reports.export;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ordertoaudit.billing.service.IndianNumberFormat;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.service.ReportPeriod;

/** How one cell is written as text. CSV gets plain machine-readable values, PDF gets values for people. */
final class CellFormat {

    private CellFormat() {
    }

    /** CSV: no grouping, no ₹, ISO dates, a dot for decimals. */
    static String plain(ColumnType type, Object value) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case TEXT, COUNT -> value.toString();
            case DATE -> value.toString();
            case MONEY, PERCENT -> ((BigDecimal) value).setScale(Money.MONEY_SCALE, Money.ROUNDING).toPlainString();
            case RATE -> ((BigDecimal) value).setScale(Money.RATE_SCALE, Money.ROUNDING).toPlainString();
            case QTY -> qty((BigDecimal) value);
        };
    }

    /** PDF: Indian digit grouping for money (1,00,000.00), dates as 05-Nov-2026. */
    static String display(ColumnType type, Object value) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case MONEY -> IndianNumberFormat.money((BigDecimal) value);
            case DATE -> ((LocalDate) value).format(ReportPeriod.DISPLAY_DATE);
            default -> plain(type, value);
        };
    }

    /** 150.0000 is shown as 150, 19.5000 as 19.5. */
    private static String qty(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }
}
