package com.ordertoaudit.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.ApiTokens;

/**
 * The exports through the real endpoint (R15): the CSV parses with an independent parser and its totals match,
 * the xlsx opens in POI with numeric cells, the PDF text has the title, period and totals.
 * State: the run is posted, and Retailer A's 150 packets are invoiced and dispatched.
 */
@AutoConfigureMockMvc
class ReportExportTest extends AbstractSalesTest {

    private static final String NOVEMBER = "from=2026-11-01&to=2026-11-30";

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void benchmarkSales() {
        produceBenchmarkStock();
        dispatch(retailerInvoice().id());
    }

    private ResultActions getAs(String username, String url) throws Exception {
        return mvc.perform(get(url).with(ApiTokens.as(mvc, username)));
    }

    private MvcResult download(String report, String format) throws Exception {
        return getAs("auditor", "/api/reports/" + report + "?" + NOVEMBER + "&format=" + format)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report + "_2026-11-01_2026-11-30." + format + "\""))
                .andReturn();
    }

    private List<CSVRecord> csv(String report) throws Exception {
        MvcResult result = download(report, "csv");
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        byte[] bytes = result.getResponse().getContentAsByteArray();
        // UTF-8 byte-order mark, so Excel reads the file as UTF-8
        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
        return CSVFormat.RFC4180.parse(new StringReader(text)).getRecords();
    }

    /** The rows of one section: from the line after its column headers up to the next empty line. */
    private List<CSVRecord> csvSection(List<CSVRecord> records, String title) {
        int start = 0;
        while (!records.get(start).get(0).equals(title)) {
            start++;
        }
        int end = start + 2;
        while (end < records.size() && !(records.get(end).size() == 1 && records.get(end).get(0).isEmpty())) {
            end++;
        }
        return records.subList(start + 2, end);
    }

    private CSVRecord csvRow(List<CSVRecord> rows, int column, String value) {
        return rows.stream().filter(r -> r.get(column).equals(value)).findFirst().orElseThrow();
    }

    @Test
    void salesRegisterCsvParsesAndItsTotalsMatch() throws Exception {
        List<CSVRecord> records = csv("sales-register");

        assertThat(records.get(0).get(0)).isEqualTo("Sales register");
        List<CSVRecord> register = csvSection(records, "Invoices and credit notes");
        assertThat(register).hasSize(2);   // the invoice and the totals row
        CSVRecord invoice = register.get(0);
        assertThat(invoice.get(1)).isEqualTo("INV/2026-27/0001");
        assertThat(invoice.get(2)).isEqualTo("2026-11-05");
        assertThat(invoice.get(7)).isEqualTo("7500.00");   // plain number: no grouping, no ₹
        CSVRecord totals = register.get(1);
        assertThat(totals.get(0)).isEqualTo("Total");
        assertThat(new BigDecimal(totals.get(11))).isEqualByComparingTo(
                new BigDecimal(invoice.get(7)).add(new BigDecimal(invoice.get(8))).add(new BigDecimal(invoice.get(9)))
                        .add(new BigDecimal(invoice.get(10))));
        assertThat(totals.get(11)).isEqualTo("7875.00");

        List<CSVRecord> products = csvSection(records, "Product-wise sales");
        assertThat(csvRow(products, 0, "MURUKKU200").get(3)).isEqualTo("150");
    }

    @Test
    void stockSummaryCsvBalancesForEveryItem() throws Exception {
        List<CSVRecord> records = csv("stock-summary");

        for (String section : new String[] {"Raw materials", "Packaging", "Finished goods"}) {
            for (CSVRecord row : csvSection(records, section)) {
                if (row.get(0).equals("Total")) {
                    continue;
                }
                BigDecimal expected = new BigDecimal(row.get(3)).add(new BigDecimal(row.get(4)))
                        .subtract(new BigDecimal(row.get(5))).add(new BigDecimal(row.get(6)));
                assertThat(new BigDecimal(row.get(7))).as(row.get(0)).isEqualByComparingTo(expected);
            }
        }
        CSVRecord flour = csvRow(csvSection(records, "Raw materials"), 0, "FLOUR");
        assertThat(List.of(flour.get(3), flour.get(4), flour.get(5), flour.get(7))).containsExactly("100", "60", "40", "120");
        assertThat(csvRow(csvSection(records, "Finished goods"), 0, "MURUKKU200").get(7)).isEqualTo("88");
    }

    private XSSFWorkbook xlsx(String report) throws Exception {
        MvcResult result = download(report, "xlsx");
        assertThat(result.getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    /** Rows 0 to 2 describe the report, row 4 has the headers, data starts at row 5. */
    private Row dataRow(Sheet sheet, String firstCell) {
        for (int r = 5; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getCell(0) != null && row.getCell(0).getStringCellValue().equals(firstCell)) {
                return row;
            }
        }
        throw new AssertionError("No row " + firstCell + " in sheet " + sheet.getSheetName());
    }

    @Test
    void salesRegisterXlsxHasNumericCellsAndAFrozenHeader() throws Exception {
        try (XSSFWorkbook workbook = xlsx("sales-register")) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            Sheet register = workbook.getSheet("Invoices and credit notes");
            assertThat(register.getRow(4).getCell(7).getStringCellValue()).isEqualTo("Taxable");
            assertThat(register.getRow(4).getCell(0).getCellStyle().getFontIndex()).isPositive();   // bold font
            assertThat(register.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);

            Row invoice = dataRow(register, "Invoice");
            assertThat(invoice.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);   // a real date
            assertThat(invoice.getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(invoice.getCell(7).getNumericCellValue()).isEqualTo(7500.0);
            assertThat(invoice.getCell(7).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            assertThat(dataRow(register, "Total").getCell(11).getNumericCellValue()).isEqualTo(7875.0);
        }
    }

    @Test
    void stockSummaryXlsxHasOneSheetPerKindWithNumericQuantities() throws Exception {
        try (XSSFWorkbook workbook = xlsx("stock-summary")) {
            assertThat(List.of(workbook.getSheetName(0), workbook.getSheetName(1), workbook.getSheetName(2)))
                    .containsExactly("Raw materials", "Packaging", "Finished goods");
            Row flour = dataRow(workbook.getSheet("Raw materials"), "FLOUR");
            assertThat(flour.getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(flour.getCell(7).getNumericCellValue()).isEqualTo(120.0);
            assertThat(dataRow(workbook.getSheet("Finished goods"), "MURUKKU200").getCell(7).getNumericCellValue())
                    .isEqualTo(88.0);
        }
    }

    @Test
    void pdfsHaveTheTitlePeriodAndTotals() throws Exception {
        String sales = pdfText(download("sales-register", "pdf").getResponse().getContentAsByteArray());
        assertThat(sales).contains("Sample Murukku Foods", "Sales register", "01-Nov-2026 to 30-Nov-2026",
                "INV/2026-27/0001", "7,500.00", "7,875.00", "Generated", "auditor");

        String stock = pdfText(download("stock-summary", "pdf").getResponse().getContentAsByteArray());
        assertThat(stock).contains("Stock summary", "01-Nov-2026 to 30-Nov-2026", "Raw materials", "Finished goods",
                "FLOUR", "120", "88");

        String production = pdfText(download("production-report", "pdf").getResponse().getContentAsByteArray());
        assertThat(production).contains("RUN/2026-27/0001", "-6.00", "⚑");   // the flag glyph is in DejaVu Sans
    }

    @Test
    void jsonIsTheDefaultAndTheDefaultPeriodIsTheCurrentMonth() throws Exception {
        // The business clock is fixed at 28 November 2026 in tests
        getAs("owner", "/api/reports/sales-register")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.generatedBy").value("owner"))
                .andExpect(jsonPath("$.sections[0].columns[7].type").value("MONEY"))
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("INV/2026-27/0001"))
                .andExpect(jsonPath("$.sections[0].totals[11]").value(7875.0));
        getAs("owner", "/api/reports/sales-register?from=2026-12-01&to=2026-12-31")
                .andExpect(jsonPath("$.sections[0].rows").isEmpty());
    }

    @Test
    void badParametersAre400() throws Exception {
        getAs("admin", "/api/reports/sales-register?format=doc").andExpect(status().isBadRequest());
        getAs("admin", "/api/reports/sales-register?from=2026-11-30&to=2026-11-01").andExpect(status().isBadRequest());
        getAs("admin", "/api/reports/sales-register?from=not-a-date").andExpect(status().isBadRequest());
        getAs("admin", "/api/reports/stock-ledger").andExpect(status().isBadRequest());
        getAs("admin", "/api/reports/stock-ledger?itemCode=NOPE").andExpect(status().isBadRequest());
        getAs("admin", "/api/reports/no-such-report").andExpect(status().isNotFound());
    }

    @Test
    void stockLedgerNamesTheSourceDocuments() throws Exception {
        getAs("production", "/api/reports/stock-ledger?itemCode=FLOUR&" + NOVEMBER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("FLOUR"))
                .andExpect(jsonPath("$.sections[0].rows.length()").value(3))
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("Opening balance"))
                .andExpect(jsonPath("$.sections[0].rows[0][7]").value(100.0))
                .andExpect(jsonPath("$.sections[0].rows[1][2]").value("Bill BENCH-1 (Sample Flour Mills)"))
                .andExpect(jsonPath("$.sections[0].rows[2][2]").value("RUN/2026-27/0001"))
                .andExpect(jsonPath("$.sections[0].totals[7]").value(120.0));
        getAs("auditor", "/api/reports/stock-ledger?itemCode=MURUKKU200&" + NOVEMBER)
                .andExpect(jsonPath("$.sections[0].rows[2][2]").value("DC/2026-27/0001 / INV/2026-27/0001"))
                .andExpect(jsonPath("$.sections[0].totals[7]").value(88.0));
        // The item code is part of the file name
        getAs("auditor", "/api/reports/stock-ledger?itemCode=FLOUR&format=csv&" + NOVEMBER)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"stock-ledger_FLOUR_2026-11-01_2026-11-30.csv\""));
    }

    @Test
    void mySalesThroughTheApiIsTheSalesmansOwn() throws Exception {
        getAs("salesman_a", "/api/reports/my-sales?" + NOVEMBER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].rows.length()").value(1))
                .andExpect(jsonPath("$.sections[0].rows[0][0]").value("RET-A"))
                .andExpect(jsonPath("$.sections[0].rows[0][3]").value(7875.0));
        getAs("salesman_b", "/api/reports/my-sales?" + NOVEMBER)
                .andExpect(jsonPath("$.sections[0].rows[0][0]").value("DIST-B"));
    }

    @Test
    void theDashboardIsJson() throws Exception {
        getAs("owner", "/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value("2026-11-28"))
                .andExpect(jsonPath("$.todaysOrders.DISPATCHED").value(1))
                .andExpect(jsonPath("$.totalOutstanding").value(7875.0));
    }
}
