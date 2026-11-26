package com.ordertoaudit.billing.service;

import java.math.BigDecimal;

import com.ordertoaudit.common.Money;

/** An amount in words using the Indian system: crore, lakh, thousand, hundred (R10.5). */
public final class AmountInWords {

    private static final String[] BELOW_TWENTY = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
            "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private static final long CRORE = 10_000_000L;
    private static final long LAKH = 100_000L;
    private static final long THOUSAND = 1_000L;
    private static final long HUNDRED = 100L;

    private AmountInWords() {
    }

    /** For example 3875.50 is "Rupees Three Thousand Eight Hundred Seventy-Five and Fifty Paise Only". */
    public static String of(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative: " + amount);
        }
        long totalPaise = Money.money(amount).movePointRight(2).longValueExact();
        long rupees = totalPaise / 100;
        long paise = totalPaise % 100;

        String paiseWords = words(paise) + (paise == 1 ? " Paisa" : " Paise");
        if (rupees == 0) {
            return (paise == 0 ? "Rupees Zero" : paiseWords) + " Only";
        }
        String text = "Rupees " + words(rupees);
        if (paise > 0) {
            text += " and " + paiseWords;
        }
        return text + " Only";
    }

    /** Peels off the largest unit first and repeats on what is left. */
    private static String words(long number) {
        if (number >= CRORE) {
            return join(words(number / CRORE) + " Crore", number % CRORE);
        }
        if (number >= LAKH) {
            return join(words(number / LAKH) + " Lakh", number % LAKH);
        }
        if (number >= THOUSAND) {
            return join(words(number / THOUSAND) + " Thousand", number % THOUSAND);
        }
        if (number >= HUNDRED) {
            return join(words(number / HUNDRED) + " Hundred", number % HUNDRED);
        }
        if (number >= 20) {
            String tens = TENS[(int) (number / 10)];
            return number % 10 == 0 ? tens : tens + "-" + BELOW_TWENTY[(int) (number % 10)];
        }
        return BELOW_TWENTY[(int) number];
    }

    private static String join(String first, long rest) {
        return rest == 0 ? first : first + " " + words(rest);
    }
}
