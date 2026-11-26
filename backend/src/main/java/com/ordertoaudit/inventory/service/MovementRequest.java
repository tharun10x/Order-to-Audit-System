package com.ordertoaudit.inventory.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.inventory.entity.Direction;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.masters.entity.AppUser;

/**
 * What a posting service asks InventoryService to record. qty is always a positive amount in the
 * item's base unit: the direction decides the sign in the ledger.
 *
 * value is the money value of an inbound movement (for a purchase: the bill amount).
 * It is null for outbound movements, because those are valued by InventoryService at the average cost (R7.4).
 */
public record MovementRequest(
        Long itemId,
        MovementType type,
        Direction direction,
        BigDecimal qty,
        BigDecimal value,
        SourceType sourceType,
        Long sourceId,
        Instant movementDate,
        String remarks,
        AppUser user) {

    /** Stock in, with its total value already known (for example the amount on a supplier bill). */
    public static MovementRequest inbound(Long itemId, MovementType type, BigDecimal qty, BigDecimal value,
                                          SourceType sourceType, Long sourceId, Instant date, AppUser user) {
        return new MovementRequest(itemId, type, Direction.IN, qty, value, sourceType, sourceId, date, null, user);
    }

    /** Stock in at a rate per base unit: value = qty x rate, rounded to 2 decimals. */
    public static MovementRequest inboundAtRate(Long itemId, MovementType type, BigDecimal qty, BigDecimal rate,
                                                SourceType sourceType, Long sourceId, Instant date, AppUser user) {
        BigDecimal value = Money.money(qty.multiply(rate));
        return inbound(itemId, type, qty, value, sourceType, sourceId, date, user);
    }

    /** Stock out, valued by InventoryService at the current weighted average cost. */
    public static MovementRequest outbound(Long itemId, MovementType type, BigDecimal qty,
                                           SourceType sourceType, Long sourceId, Instant date, AppUser user) {
        return new MovementRequest(itemId, type, Direction.OUT, qty, null, sourceType, sourceId, date, null, user);
    }

    public MovementRequest withRemarks(String newRemarks) {
        return new MovementRequest(itemId, type, direction, qty, value, sourceType, sourceId, movementDate, newRemarks, user);
    }
}
