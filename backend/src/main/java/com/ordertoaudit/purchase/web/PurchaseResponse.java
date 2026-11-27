package com.ordertoaudit.purchase.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ordertoaudit.purchase.entity.PurchaseStatus;

/** A recorded purchase. Line qty and rate are in the item's base unit. */
public record PurchaseResponse(
        Long id,
        Long supplierId,
        String supplierName,
        LocalDate purchaseDate,
        String billNo,
        PurchaseStatus status,
        BigDecimal total,
        List<Line> lines) {

    /** qty and rate are in unit, the item's base unit. */
    public record Line(String itemCode, String itemName, BigDecimal qty, String unit, BigDecimal rate,
                       BigDecimal amount) {
    }
}
