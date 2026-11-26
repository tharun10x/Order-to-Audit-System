package com.ordertoaudit.common.error;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * R6.6: an outgoing movement would take stock below zero. Becomes HTTP 409.
 * One exception can carry several shortages (R8: a failed production post shows the whole shortage list).
 */
public class InsufficientStockException extends ConflictException {

    /** One short item: what is on hand, what was asked for, and the difference. */
    public record Shortage(String itemCode, BigDecimal available, BigDecimal requested) {

        public BigDecimal shortBy() {
            return requested.subtract(available);
        }

        public String describe() {
            return itemCode + ": available " + available.toPlainString()
                    + ", requested " + requested.toPlainString()
                    + ", short by " + shortBy().toPlainString();
        }
    }

    private final List<Shortage> shortages;

    public InsufficientStockException(String itemCode, BigDecimal available, BigDecimal requested) {
        this(List.of(new Shortage(itemCode, available, requested)));
    }

    public InsufficientStockException(List<Shortage> shortages) {
        super(buildMessage(shortages));
        this.shortages = List.copyOf(shortages);
    }

    private static String buildMessage(List<Shortage> shortages) {
        if (shortages.size() == 1) {
            return "Insufficient stock for " + shortages.get(0).describe();
        }
        return "Insufficient stock for " + shortages.size() + " items: "
                + shortages.stream().map(Shortage::describe).collect(Collectors.joining("; "));
    }

    public List<Shortage> getShortages() {
        return shortages;
    }

    /** The first short item, for callers that only care about one. */
    public String getItemCode() {
        return shortages.get(0).itemCode();
    }

    public BigDecimal getShortage() {
        return shortages.get(0).shortBy();
    }
}
