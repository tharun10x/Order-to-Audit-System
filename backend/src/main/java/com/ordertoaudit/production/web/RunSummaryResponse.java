package com.ordertoaudit.production.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ordertoaudit.production.entity.RunStatus;

/** One row of GET /api/production-runs. */
public record RunSummaryResponse(
        Long id,
        String runNo,
        String productCode,
        LocalDate runDate,
        RunStatus status,
        BigDecimal batches,
        BigDecimal plannedOutput,
        BigDecimal actualOutput) {
}
