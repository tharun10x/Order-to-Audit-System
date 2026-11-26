package com.ordertoaudit.reports.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * Excel .xlsx with Apache POI: one sheet per section. Numbers and dates are real numeric cells with a number
 * format, so Excel can add them up. Header row bold and frozen, totals row bold.
 */
@Component
public class XlsxExporter {

    /** Rows 0 to 2 describe the report, row 3 is empty, row 4 holds the column headers. */
    private static final int HEADER_ROW = 4;
    private static final int MAX_SHEET_NAME = 31;
    private static final int MAX_COLUMN_CHARS = 60;
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public byte[] export(Report report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Set<String> sheetNames = new HashSet<>();
            for (ReportTable table : report.sections()) {
                Sheet sheet = workbook.createSheet(uniqueSheetName(table.title(), sheetNames));
                writeSheet(sheet, report, table, styles);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the Excel file", e);
        }
    }

    private void writeSheet(Sheet sheet, Report report, ReportTable table, Styles styles) {
        text(sheet.createRow(0), 0, report.title() + " — " + table.title(), styles.bold);
        text(sheet.createRow(1), 0, report.companyName() + " · " + report.periodLabel()
                + (report.subject() == null ? "" : " · " + report.subject()), null);
        text(sheet.createRow(2), 0, "Generated " + report.generatedAt().atZone(Money.ZONE).format(GENERATED)
                + " by " + report.generatedBy(), null);

        int[] widths = new int[table.columns().size()];
        Row header = sheet.createRow(HEADER_ROW);
        for (int c = 0; c < table.columns().size(); c++) {
            String title = table.columns().get(c).header();
            text(header, c, title, styles.bold);
            widths[c] = title.length();
        }

        int rowIndex = HEADER_ROW + 1;
        for (List<Object> values : table.rows()) {
            writeRow(sheet.createRow(rowIndex++), table, values, styles, false, widths);
        }
        if (table.totals() != null) {
            writeRow(sheet.createRow(rowIndex), table, table.totals(), styles, true, widths);
        }

        sheet.createFreezePane(0, HEADER_ROW + 1);
        // Widths from the text length: autoSizeColumn needs desktop fonts, which a server may not have
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, (Math.min(widths[c], MAX_COLUMN_CHARS) + 3) * 256);
        }
    }

    private void writeRow(Row row, ReportTable table, List<Object> values, Styles styles, boolean bold, int[] widths) {
        for (int c = 0; c < values.size(); c++) {
            Object value = values.get(c);
            if (value == null) {
                continue;
            }
            ColumnType type = table.columns().get(c).type();
            Cell cell = row.createCell(c);
            if (value instanceof BigDecimal number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Long count) {
                cell.setCellValue(count);
            } else if (value instanceof LocalDate date) {
                cell.setCellValue(date);
            } else {
                // A String cell is stored as text: Excel never runs it as a formula (F10)
                cell.setCellValue(value.toString());
            }
            CellStyle style = styles.get(type, bold);
            if (style != null) {
                cell.setCellStyle(style);
            }
            widths[c] = Math.max(widths[c], CellFormat.display(type, value).length());
        }
    }

    private void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /** Excel allows 31 characters and no : \ / ? * [ ], and every sheet name must be different. */
    private String uniqueSheetName(String title, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(title);
        if (base.length() > MAX_SHEET_NAME) {
            base = base.substring(0, MAX_SHEET_NAME);
        }
        String name = base;
        for (int n = 2; !used.add(name.toLowerCase()); n++) {
            String suffix = " (" + n + ")";
            name = base.substring(0, Math.min(base.length(), MAX_SHEET_NAME - suffix.length())) + suffix;
        }
        return name;
    }

    /** One cell style per column type, plain and bold. A workbook can hold only a limited number of styles. */
    private static final class Styles {

        private final CellStyle bold;
        private final Map<ColumnType, CellStyle> normal = new EnumMap<>(ColumnType.class);
        private final Map<ColumnType, CellStyle> boldByType = new EnumMap<>(ColumnType.class);

        Styles(Workbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            for (ColumnType type : ColumnType.values()) {
                normal.put(type, style(workbook, type, null));
                boldByType.put(type, style(workbook, type, boldFont));
            }
        }

        CellStyle get(ColumnType type, boolean isBold) {
            return isBold ? boldByType.get(type) : normal.get(type);
        }

        private static CellStyle style(Workbook workbook, ColumnType type, Font font) {
            CellStyle style = workbook.createCellStyle();
            if (font != null) {
                style.setFont(font);
            }
            String format = switch (type) {
                case MONEY -> "#,##0.00";
                case RATE -> "#,##0.0000";
                case PERCENT -> "0.00";
                case COUNT -> "0";
                case DATE -> "dd-mmm-yyyy";
                case QTY -> "General";   // 150 and 19.5 without trailing zeros
                case TEXT -> null;
            };
            if (format != null) {
                style.setDataFormat(workbook.createDataFormat().getFormat(format));
            }
            if (type.isNumeric()) {
                style.setAlignment(HorizontalAlignment.RIGHT);
            }
            return style;
        }
    }
}
