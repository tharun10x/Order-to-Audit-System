package com.ordertoaudit.masters.web;

import java.util.List;

import com.ordertoaudit.masters.entity.ItemKind;

/**
 * An active stocked item. units are the unit codes it can be entered in, base unit first, by the same rules the
 * backend converts with (R3.3). hasActiveBom is true only for a finished good that can be produced.
 */
public record ItemResponse(String code, String name, ItemKind kind, String baseUnit, List<String> units,
                           boolean hasActiveBom) {
}
