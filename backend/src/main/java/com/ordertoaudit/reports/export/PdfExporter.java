package com.ordertoaudit.reports.export;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.pdf.HtmlPdfRenderer;
import com.ordertoaudit.reports.model.Column;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/** Any report as a landscape A4 PDF, through the one generic template templates/report/report.html. */
@Component
public class PdfExporter {

    private static final String TEMPLATE = "report/report";
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    /** What the template prints: every value already formatted as text. */
    public record View(String title, String companyName, String periodLabel, String subject, String generated,
                       List<Section> sections) {
    }

    /** numeric[i] right-aligns column i. totals is null when the table has none. */
    public record Section(String title, List<String> headers, List<Boolean> numeric, List<List<String>> rows,
                          List<String> totals) {
    }

    private final HtmlPdfRenderer pdfRenderer;

    public PdfExporter(HtmlPdfRenderer pdfRenderer) {
        this.pdfRenderer = pdfRenderer;
    }

    public byte[] export(Report report) {
        List<Section> sections = report.sections().stream().map(this::section).toList();
        String generated = report.generatedAt().atZone(Money.ZONE).format(GENERATED) + " IST by " + report.generatedBy();
        View view = new View(report.title(), report.companyName(), report.periodLabel(), report.subject(), generated,
                sections);
        return pdfRenderer.render(TEMPLATE, Map.of("report", view));
    }

    private Section section(ReportTable table) {
        List<String> headers = table.columns().stream().map(Column::header).toList();
        List<Boolean> numeric = table.columns().stream().map(column -> column.type().isNumeric()).toList();
        List<List<String>> rows = new ArrayList<>();
        for (List<Object> row : table.rows()) {
            rows.add(cells(table, row));
        }
        return new Section(table.title(), headers, numeric, rows,
                table.totals() == null ? null : cells(table, table.totals()));
    }

    private List<String> cells(ReportTable table, List<Object> values) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            cells.add(CellFormat.display(table.columns().get(i).type(), values.get(i)));
        }
        return cells;
    }
}
