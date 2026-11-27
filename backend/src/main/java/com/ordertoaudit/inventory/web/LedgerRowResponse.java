package com.ordertoaudit.inventory.web;

import java.math.BigDecimal;
import java.time.Instant;

import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;

/**
 * One movement in GET /api/inventory/ledger, with the running balance after it. qty and value are signed.
 * documentNo names the source document (R6.2), as the stock ledger report does; invoiceId is set for a dispatch or
 * a credit note, so a screen can link to the invoice. A run's id is sourceId.
 */
public record LedgerRowResponse(
        Long id,
        Instant movementDate,
        MovementType movementType,
        SourceType sourceType,
        Long sourceId,
        String documentNo,
        Long invoiceId,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal value,
        BigDecimal runningQty,
        BigDecimal runningValue,
        String remarks) {
}
