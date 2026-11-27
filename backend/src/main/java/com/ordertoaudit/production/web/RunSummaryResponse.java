package com.ordertoaudit.production.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ordertoaudit.production.entity.RunStatus;

/**
 * One row of GET /api/production-runs. yieldVariancePct is null until the good output is entered; costPerPacket
 * (2 decimals, overheads included, R13.5) is null until the run is posted.
 */
public record RunSummaryResponse(
        Long id,
        String runNo,
        String productCode,
        LocalDate runDate,
        RunStatus status,
        BigDecimal batches,
        BigDecimal plannedOutput,
        BigDecimal actualOutput,
        BigDecimal yieldVariancePct,
        boolean yieldFlagged,
        BigDecimal costPerPacket) {
}
