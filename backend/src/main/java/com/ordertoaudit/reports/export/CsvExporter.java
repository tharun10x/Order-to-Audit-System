package com.ordertoaudit.reports.export;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.reports.model.Column;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * CSV (RFC 4180): UTF-8 with a byte-order mark so Excel reads it as UTF-8, CRLF line ends, plain numbers.
 * A few heading lines, then each section: its title, the column headers, the rows and the totals row.
 */
@Component
public class CsvExporter {

    private static final String BOM = "﻿";
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] export(Report report) {
        StringBuilder csv = new StringBuilder(BOM);
        line(csv, List.of(report.title()));
        line(csv, List.of("Company", report.companyName()));
        line(csv, List.of("Period", report.periodLabel()));
        if (report.subject() != null) {
            line(csv, List.of("Item", report.subject()));
        }
        line(csv, List.of("Generated", report.generatedAt().atZone(Money.ZONE).format(GENERATED), report.generatedBy()));
        for (ReportTable table : report.sections()) {
            csv.append(LINE_END);
            line(csv, List.of(table.title()));
            line(csv, table.columns().stream().map(Column::header).toList());
            for (List<Object> row : table.rows()) {
                line(csv, cells(table, row));
            }
            if (table.totals() != null) {
                line(csv, cells(table, table.totals()));
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<String> cells(ReportTable table, List<Object> values) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            cells.add(CellFormat.plain(table.columns().get(i).type(), values.get(i)));
        }
        return cells;
    }

    private void line(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(cells.get(i)));
        }
        csv.append(LINE_END);
    }

    /**
     * Quotes a cell that holds a comma, quote or line break. A cell that a spreadsheet would run as a formula
     * (starting with = + - @, a tab or a carriage return) gets a leading apostrophe, so a customer named
     * "=HYPERLINK(...)" stays text (F10). Numbers are written by CellFormat and never start that way except
     * negative ones, which are numeric columns and are left alone: only text cells reach here unchanged.
     */
    static String escape(String cell) {
        String text = cell;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !isNumber(text)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** A plain negative amount such as -7875.00 is a number, not a formula. */
    private static boolean isNumber(String text) {
        return text.matches("-\\d+(\\.\\d+)?");
    }
}
