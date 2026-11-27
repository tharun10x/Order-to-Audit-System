package com.ordertoaudit.inventory.web;

import java.math.BigDecimal;

import com.ordertoaudit.masters.entity.ItemKind;

/** One row of GET /api/inventory/balances. unit is the base unit. avgCost is null when nothing is on hand. */
public record BalanceResponse(
        Long itemId,
        String itemCode,
        String itemName,
        ItemKind kind,
        String unit,
        BigDecimal qtyOnHand,
        BigDecimal stockValue,
        BigDecimal avgCost) {
}
