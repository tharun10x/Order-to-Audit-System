package com.ordertoaudit.reports.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.ReportTable;
import com.ordertoaudit.reports.service.ReportPeriod;

/** The pieces of the exports that need no database. */
class ExportFormattingTest {

    @Test
    void csvCellsThatASpreadsheetWouldRunAsFormulasStayText() {
        assertThat(CsvExporter.escape("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(CsvExporter.escape("+91 98765")).isEqualTo("'+91 98765");
        assertThat(CsvExporter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvExporter.escape("-cmd")).isEqualTo("'-cmd");
        // Negative amounts are numbers, and ordinary text is untouched apart from quoting
        assertThat(CsvExporter.escape("-7875.00")).isEqualTo("-7875.00");
        assertThat(CsvExporter.escape("Tiruchirappalli, Tamil Nadu")).isEqualTo("\"Tiruchirappalli, Tamil Nadu\"");
        assertThat(CsvExporter.escape("Retailer A")).isEqualTo("Retailer A");
    }

    @Test
    void cellsAreWrittenByColumnType() {
        assertThat(CellFormat.plain(ColumnType.QTY, new BigDecimal("150.0000"))).isEqualTo("150");
        assertThat(CellFormat.plain(ColumnType.QTY, new BigDecimal("19.5000"))).isEqualTo("19.5");
        assertThat(CellFormat.plain(ColumnType.MONEY, new BigDecimal("100000"))).isEqualTo("100000.00");
        assertThat(CellFormat.display(ColumnType.MONEY, new BigDecimal("100000"))).isEqualTo("1,00,000.00");
        assertThat(CellFormat.plain(ColumnType.RATE, new BigDecimal("43.125"))).isEqualTo("43.1250");
        assertThat(CellFormat.plain(ColumnType.DATE, LocalDate.of(2026, 11, 5))).isEqualTo("2026-11-05");
        assertThat(CellFormat.display(ColumnType.DATE, LocalDate.of(2026, 11, 5))).isEqualTo("05-Nov-2026");
        assertThat(CellFormat.plain(ColumnType.MONEY, null)).isEmpty();
    }

    @Test
    void aTableRefusesValuesThatDoNotFitItsColumns() {
        ReportTable.Builder table = ReportTable.builder("T")
                .column("name", "Name", ColumnType.TEXT)
                .column("amount", "Amount", ColumnType.MONEY);

        assertThatThrownBy(() -> table.row("only one")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> table.row("x", 5.0)).isInstanceOf(IllegalArgumentException.class);   // no doubles
        assertThat(table.row("x", null).build().rows()).hasSize(1);
    }

    @Test
    void theDefaultPeriodIsTheBusinessMonth() {
        Clock lateNovember = Clock.fixed(LocalDate.of(2026, 11, 28).atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata"))
                .toInstant(), ZoneId.of("Asia/Kolkata"));

        ReportPeriod period = ReportPeriod.resolve(null, null, lateNovember);
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(period.label()).isEqualTo("01-Nov-2026 to 30-Nov-2026");
        assertThat(ReportPeriod.resolve(LocalDate.of(2026, 10, 15), null, lateNovember).to())
                .isEqualTo(LocalDate.of(2026, 11, 30));
        assertThatThrownBy(() -> ReportPeriod.resolve(LocalDate.of(2026, 12, 1), null, lateNovember))
                .isInstanceOf(BadRequestException.class);
        // The period ends at midnight IST after "to"
        assertThat(period.endExclusive().toString()).isEqualTo("2026-11-30T18:30:00Z");
    }

    @Test
    void unknownFormatsAreRefused() {
        assertThat(ReportFormat.parse("XLSX")).isEqualTo(ReportFormat.XLSX);
        assertThatThrownBy(() -> ReportFormat.parse("doc")).isInstanceOf(BadRequestException.class);
    }
}
