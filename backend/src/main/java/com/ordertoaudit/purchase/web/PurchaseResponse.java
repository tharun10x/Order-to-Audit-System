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

    public record Line(String itemCode, BigDecimal qty, BigDecimal rate, BigDecimal amount) {
    }
}
