package com.ordertoaudit.masters.web;

import java.math.BigDecimal;
import java.util.List;

import com.ordertoaudit.masters.entity.ItemKind;

/**
 * A stocked item. units are the unit codes it can be entered in, base unit first, by the same rules the backend
 * converts with (R3.3). hasActiveBom is true only for a finished good that can be produced.
 * hsnCode, gstRate, sellingPrice, packWeightKg and packetsPerCarton are for finished goods (R5.1), null otherwise.
 * baseUnitLockReason says why the base unit can no longer change (M7), null while it still can.
 */
public record ItemResponse(String code, String name, ItemKind kind, String baseUnit, List<String> units,
                           boolean hasActiveBom, BigDecimal reorderLevel, String hsnCode, BigDecimal gstRate,
                           BigDecimal sellingPrice, BigDecimal packWeightKg, Integer packetsPerCarton,
                           boolean active, String baseUnitLockReason) {
}
