package com.ordertoaudit.production.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.production.entity.OverheadType;
import com.ordertoaudit.production.entity.RunStatus;

/**
 * A production run with its variance and cost report (R8.7 to R8.9, R13). All of it is calculated when asked
 * for, from the run's own copied standards and the ledger, so nothing here can go out of date.
 * costing is null until the run is POSTED. Variances are null where they cannot be calculated yet.
 */
public record RunResponse(
        Long id,
        String runNo,
        String productCode,
        String productName,
        LocalDate runDate,
        RunStatus status,
        BigDecimal batches,
        BigDecimal plannedOutput,
        BigDecimal actualOutput,
        BigDecimal yieldVariancePct,
        boolean yieldFlagged,
        BigDecimal tolerancePct,
        Instant postedAt,
        String postedBy,
        List<Line> lines,
        List<Overhead> overheads,
        Costing costing) {

    /** variancePct = (actual - planned) / planned x 100 at 2 decimals. flagged when |variancePct| > tolerance. */
    public record Line(
            String itemCode,
            String itemName,
            ItemKind kind,
            /** The base unit of plannedQty, actualQty and scrapQty. */
            String unit,
            BigDecimal plannedQty,
            BigDecimal actualQty,
            BigDecimal scrapQty,
            BigDecimal unitCost,
            BigDecimal value,
            BigDecimal variancePct,
            boolean flagged) {
    }

    public record Overhead(Long id, OverheadType type, BigDecimal amount, String note) {
    }

    /**
     * costPerPacket has 4 decimals (31.1436) and costPerPacketRounded has 2 (31.14, for display).
     * The margin uses the 4-decimal cost and is rounded only for display (R4.4).
     */
    public record Costing(
            BigDecimal materialCost,
            BigDecimal packagingCost,
            Map<OverheadType, BigDecimal> overheadsByType,
            BigDecimal overheadTotal,
            BigDecimal totalCost,
            BigDecimal costPerPacket,
            BigDecimal costPerPacketRounded,
            BigDecimal sellingPrice,
            BigDecimal marginPerPacket,
            BigDecimal marginPerPacketRounded,
            BigDecimal marginPct) {
    }
}
