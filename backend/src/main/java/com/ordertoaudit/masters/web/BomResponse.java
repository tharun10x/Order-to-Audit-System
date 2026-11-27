package com.ordertoaudit.masters.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ordertoaudit.masters.entity.Bom;
import com.ordertoaudit.masters.entity.BomLine;
import com.ordertoaudit.masters.entity.BomLineKind;

/** One recipe version: packets per batch and the standard quantity of each item per batch, in its base unit. */
public record BomResponse(Long id, String productCode, String productName, int version, BigDecimal outputQty,
                          boolean active, Instant createdAt, List<Line> lines) {

    public record Line(String itemCode, String itemName, BomLineKind lineKind, BigDecimal qty, String unit) {
    }

    /** Call inside a transaction: product, lines and their items are loaded lazily. */
    public static BomResponse from(Bom bom) {
        List<Line> lines = bom.getLines().stream().map(BomResponse::line).toList();
        return new BomResponse(bom.getId(), bom.getProduct().getCode(), bom.getProduct().getName(), bom.getVersion(),
                bom.getOutputQty(), bom.isActive(), bom.getCreatedAt(), lines);
    }

    private static Line line(BomLine line) {
        return new Line(line.getItem().getCode(), line.getItem().getName(), line.getLineKind(), line.getQty(),
                line.getItem().getBaseUom().getCode());
    }
}
