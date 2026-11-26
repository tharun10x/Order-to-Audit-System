package com.ordertoaudit.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.ordertoaudit.billing.service.AmountInWords;
import com.ordertoaudit.billing.service.IndianNumberFormat;

/** R10.5: how the amounts are written on the invoice. No database. */
class AmountInWordsTest {

    private String words(String amount) {
        return AmountInWords.of(new BigDecimal(amount));
    }

    @Test
    void wholeRupees() {
        assertThat(words("7875.00")).isEqualTo("Rupees Seven Thousand Eight Hundred Seventy-Five Only");
    }

    @Test
    void rupeesAndPaise() {
        assertThat(words("3875.50"))
                .isEqualTo("Rupees Three Thousand Eight Hundred Seventy-Five and Fifty Paise Only");
    }

    @Test
    void lakhAndCroreAreUsed() {
        assertThat(words("100000")).isEqualTo("Rupees One Lakh Only");
        assertThat(words("12345678"))
                .isEqualTo("Rupees One Crore Twenty-Three Lakh Forty-Five Thousand Six Hundred Seventy-Eight Only");
        assertThat(words("1000000000")).isEqualTo("Rupees One Hundred Crore Only");
    }

    @Test
    void paiseOnly() {
        assertThat(words("0.50")).isEqualTo("Fifty Paise Only");
        assertThat(words("0.01")).isEqualTo("One Paisa Only");
        assertThat(words("0")).isEqualTo("Rupees Zero Only");
    }

    @Test
    void digitsAreGroupedTheIndianWay() {
        assertThat(IndianNumberFormat.money(new BigDecimal("0.5"))).isEqualTo("0.50");
        assertThat(IndianNumberFormat.money(new BigDecimal("999"))).isEqualTo("999.00");
        assertThat(IndianNumberFormat.money(new BigDecimal("7875"))).isEqualTo("7,875.00");
        assertThat(IndianNumberFormat.money(new BigDecimal("100000"))).isEqualTo("1,00,000.00");
        assertThat(IndianNumberFormat.money(new BigDecimal("12345678.9"))).isEqualTo("1,23,45,678.90");
        assertThat(IndianNumberFormat.money(new BigDecimal("-1234.5"))).isEqualTo("-1,234.50");
    }
}
