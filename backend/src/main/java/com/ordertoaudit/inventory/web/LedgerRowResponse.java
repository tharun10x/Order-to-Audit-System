package com.ordertoaudit.inventory.web;

import java.math.BigDecimal;
import java.time.Instant;

import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;

/** One movement in GET /api/inventory/ledger, with the running balance after it. qty and value are signed. */
public record LedgerRowResponse(
        Long id,
        Instant movementDate,
        MovementType movementType,
        SourceType sourceType,
        Long sourceId,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal value,
        BigDecimal runningQty,
        BigDecimal runningValue,
        String remarks) {
}
