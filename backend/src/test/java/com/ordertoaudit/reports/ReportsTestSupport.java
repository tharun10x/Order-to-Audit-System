package com.ordertoaudit.reports;

import java.math.BigDecimal;
import java.util.List;

import com.ordertoaudit.reports.model.Column;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/** Reads cells of a built report by section title, row and column key, so tests say what they check. */
public final class ReportsTestSupport {

    private ReportsTestSupport() {
    }

    public static ReportTable section(Report report, String title) {
        return report.sections().stream().filter(s -> s.title().equals(title)).findFirst()
                .orElseThrow(() -> new AssertionError("No section '" + title + "' in " + report.code()));
    }

    /** The first row whose column "key" holds "value". */
    public static List<Object> row(ReportTable table, String key, Object value) {
        int column = index(table, key);
        return table.rows().stream().filter(r -> value.equals(r.get(column))).findFirst()
                .orElseThrow(() -> new AssertionError("No row with " + key + " = " + value + " in " + table.title()));
    }

    public static Object cell(ReportTable table, List<Object> row, String key) {
        return row.get(index(table, key));
    }

    public static BigDecimal number(ReportTable table, List<Object> row, String key) {
        return (BigDecimal) cell(table, row, key);
    }

    public static BigDecimal total(ReportTable table, String key) {
        return (BigDecimal) table.totals().get(index(table, key));
    }

    public static int index(ReportTable table, String key) {
        List<String> keys = table.columns().stream().map(Column::key).toList();
        int index = keys.indexOf(key);
        if (index < 0) {
            throw new AssertionError("No column '" + key + "' in " + table.title() + ": " + keys);
        }
        return index;
    }
}
