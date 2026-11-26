package com.ordertoaudit.reports.service;

import java.math.BigDecimal;

import com.ordertoaudit.common.Money;

/** A running total of taxable value and the three taxes, for grouping report rows. */
final class TaxAmounts {

    BigDecimal taxable = zero();
    BigDecimal cgst = zero();
    BigDecimal sgst = zero();
    BigDecimal igst = zero();

    void add(BigDecimal taxableValue, BigDecimal cgstValue, BigDecimal sgstValue, BigDecimal igstValue) {
        taxable = taxable.add(taxableValue);
        cgst = cgst.add(cgstValue);
        sgst = sgst.add(sgstValue);
        igst = igst.add(igstValue);
    }

    void add(SalesFacts.Line line) {
        add(line.signed(line.taxableValue()), line.signed(line.cgst()), line.signed(line.sgst()),
                line.signed(line.igst()));
    }

    void add(TaxAmounts other) {
        add(other.taxable, other.cgst, other.sgst, other.igst);
    }

    BigDecimal tax() {
        return cgst.add(sgst).add(igst);
    }

    BigDecimal total() {
        return taxable.add(tax());
    }

    static BigDecimal zero() {
        return Money.money(BigDecimal.ZERO);
    }
}
