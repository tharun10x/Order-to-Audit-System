package com.ordertoaudit.billing.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import com.ordertoaudit.billing.entity.SupplyType;
import com.ordertoaudit.common.Money;

/** The GST arithmetic (R10.3, R10.4). Pure: no database and no Spring, so it is easy to test. */
public final class GstCalculator {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    /** Half of a percentage: qty x rate x gstRate / 200 is the CGST, and again the SGST. */
    private static final BigDecimal HALF_PERCENT = BigDecimal.valueOf(200);
    private static final BigDecimal ZERO = Money.money(BigDecimal.ZERO);

    /** The amounts of one invoice line, or the sum of all lines. */
    public record Tax(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {

        public BigDecimal total() {
            return taxable.add(cgst).add(sgst).add(igst);
        }
    }

    private GstCalculator() {
    }

    /** R10.3: the same state as the factory is INTRA, any other state is INTER. */
    public static SupplyType supplyType(String sellerStateCode, String buyerStateCode) {
        return sellerStateCode.equals(buyerStateCode) ? SupplyType.INTRA : SupplyType.INTER;
    }

    /** R10.4, R4.3: each line is rounded on its own. */
    public static Tax line(BigDecimal qty, BigDecimal rate, BigDecimal gstRate, SupplyType supplyType) {
        BigDecimal taxable = Money.money(qty.multiply(rate));
        if (supplyType == SupplyType.INTRA) {
            // One division, so the half rate is never rounded before the tax is
            BigDecimal half = taxable.multiply(gstRate).divide(HALF_PERCENT, Money.MONEY_SCALE, Money.ROUNDING);
            return new Tax(taxable, half, half, ZERO);
        }
        BigDecimal igst = taxable.multiply(gstRate).divide(PERCENT, Money.MONEY_SCALE, Money.ROUNDING);
        return new Tax(taxable, ZERO, ZERO, igst);
    }

    /** R4.3: the invoice totals are the sums of the rounded lines. */
    public static Tax sum(List<Tax> lines) {
        return new Tax(add(lines, Tax::taxable), add(lines, Tax::cgst), add(lines, Tax::sgst), add(lines, Tax::igst));
    }

    private static BigDecimal add(List<Tax> lines, Function<Tax, BigDecimal> part) {
        return lines.stream().map(part).reduce(ZERO, BigDecimal::add);
    }
}
