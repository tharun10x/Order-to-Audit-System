package com.ordertoaudit.sales.web;

import java.math.BigDecimal;
import java.util.List;

/** R9.4: what the approval screen shows. All quantities in packets. */
public record StockCheckResponse(Long orderId, String orderNo, boolean canApprove, List<Line> lines) {

    /** batchesNeeded is 0 without a shortage, and null if the product has no active BOM. */
    public record Line(String itemCode, BigDecimal ordered, BigDecimal onHand, BigDecimal reserved,
                       BigDecimal available, BigDecimal shortage, Integer batchesNeeded) {
    }
}
