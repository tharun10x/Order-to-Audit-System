package com.ordertoaudit.reports.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A finished report: what the JSON format returns and what every exporter renders.
 * code is used in the download file name (sales-register_2026-11-01_2026-11-30.xlsx).
 * periodLabel is how the period is printed ("01-Nov-2026 to 30-Nov-2026", or "As of 30-Nov-2026").
 */
public record Report(
        String code,
        String title,
        String subject,
        LocalDate from,
        LocalDate to,
        String periodLabel,
        String companyName,
        Instant generatedAt,
        String generatedBy,
        List<ReportTable> sections) {
}
