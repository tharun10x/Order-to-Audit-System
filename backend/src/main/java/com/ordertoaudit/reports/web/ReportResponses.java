package com.ordertoaudit.reports.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.ordertoaudit.reports.export.CsvExporter;
import com.ordertoaudit.reports.export.PdfExporter;
import com.ordertoaudit.reports.export.ReportFormat;
import com.ordertoaudit.reports.export.XlsxExporter;
import com.ordertoaudit.reports.model.Report;

/** Turns a report into the HTTP answer for the requested format: JSON, or a download. */
@Component
public class ReportResponses {

    private final CsvExporter csvExporter;
    private final XlsxExporter xlsxExporter;
    private final PdfExporter pdfExporter;

    public ReportResponses(CsvExporter csvExporter, XlsxExporter xlsxExporter, PdfExporter pdfExporter) {
        this.csvExporter = csvExporter;
        this.xlsxExporter = xlsxExporter;
        this.pdfExporter = pdfExporter;
    }

    public ResponseEntity<Object> respond(Report report, ReportFormat format) {
        if (format == ReportFormat.JSON) {
            return ResponseEntity.ok(report);
        }
        byte[] content = switch (format) {
            case CSV -> csvExporter.export(report);
            case XLSX -> xlsxExporter.export(report);
            case PDF -> pdfExporter.export(report);
            case JSON -> throw new IllegalStateException("handled above");
        };
        return ResponseEntity.ok()
                .contentType(format.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName(report, format)).build().toString())
                .body(content);
    }

    /** For example sales-register_2026-11-01_2026-11-30.xlsx, or stock-ledger_FLOUR_2026-11-01_2026-11-30.csv. */
    static String fileName(Report report, ReportFormat format) {
        String subject = report.subject() == null ? "" : "_" + report.subject().replaceAll("[^A-Za-z0-9-]", "-");
        return report.code() + subject + "_" + report.from() + "_" + report.to() + "." + format.extension();
    }
}
