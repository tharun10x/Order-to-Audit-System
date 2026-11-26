package com.ordertoaudit.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ordertoaudit.billing.entity.SupplyType;
import com.ordertoaudit.billing.service.GstCalculator;

/** R10.3, R10.4, R4.3. No database. */
class GstCalculatorTest {

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("5.00");

    private GstCalculator.Tax line(String qty, String rate, SupplyType type) {
        return GstCalculator.line(new BigDecimal(qty), new BigDecimal(rate), FIVE_PERCENT, type);
    }

    @Test
    void theStateDecidesTheSupplyType() {
        assertThat(GstCalculator.supplyType("33", "33")).isEqualTo(SupplyType.INTRA);
        assertThat(GstCalculator.supplyType("33", "29")).isEqualTo(SupplyType.INTER);
    }

    @Test
    void karnatakaPaysIgstOn120Packets() {
        GstCalculator.Tax tax = line("120", "50", GstCalculator.supplyType("33", "29"));

        assertThat(tax.taxable().toPlainString()).isEqualTo("6000.00");
        assertThat(tax.igst().toPlainString()).isEqualTo("300.00");
        assertThat(tax.cgst().toPlainString()).isEqualTo("0.00");
        assertThat(tax.sgst().toPlainString()).isEqualTo("0.00");
        assertThat(tax.total().toPlainString()).isEqualTo("6300.00");
    }

    @Test
    void karnatakaPaysIgstOn88Packets() {
        GstCalculator.Tax tax = line("88", "50", SupplyType.INTER);

        assertThat(tax.igst().toPlainString()).isEqualTo("220.00");
        assertThat(tax.total().toPlainString()).isEqualTo("4620.00");
    }

    @Test
    void eachHalfIsRoundedHalfUpOnItsOwn() {
        // 2.5% of 10.20 is exactly 0.255, so each half rounds up to 0.26 (0.52, while 5% would be 0.51)
        GstCalculator.Tax intra = line("1", "10.20", SupplyType.INTRA);
        assertThat(intra.cgst().toPlainString()).isEqualTo("0.26");
        assertThat(intra.sgst().toPlainString()).isEqualTo("0.26");

        assertThat(line("1", "10.20", SupplyType.INTER).igst().toPlainString()).isEqualTo("0.51");
    }

    @Test
    void invoiceTotalsAreTheSumOfTheRoundedLines() {
        GstCalculator.Tax one = line("1", "10.20", SupplyType.INTRA);

        GstCalculator.Tax totals = GstCalculator.sum(List.of(one, one));

        // R4.3: 0.26 + 0.26. Taxing the 20.40 total instead would give 0.51
        assertThat(totals.taxable().toPlainString()).isEqualTo("20.40");
        assertThat(totals.cgst().toPlainString()).isEqualTo("0.52");
        assertThat(totals.total().toPlainString()).isEqualTo("21.44");
    }
}
