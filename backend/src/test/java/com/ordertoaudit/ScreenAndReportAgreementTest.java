package com.ordertoaudit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.common.error.UnitMismatchException;
import com.ordertoaudit.inventory.service.LedgerService;
import com.ordertoaudit.inventory.web.LedgerRowResponse;
import com.ordertoaudit.masters.service.ItemService;
import com.ordertoaudit.masters.service.UnitConversionService;
import com.ordertoaudit.masters.web.ItemResponse;
import com.ordertoaudit.payments.web.CustomerOutstandingResponse;
import com.ordertoaudit.reports.ReportsTestSupport;
import com.ordertoaudit.reports.model.ReportTable;
import com.ordertoaudit.reports.service.OutstandingReport;
import com.ordertoaudit.reports.service.ReportPeriod;
import com.ordertoaudit.reports.service.StockLedgerReport;

/**
 * The screen endpoints reuse the reports' and the converter's logic instead of copying it, so they must give the same
 * answers. Benchmark data: the run posted, Retailer A invoiced (5 Nov), dispatched (6 Nov) and paid 4,000.00 (10 Nov).
 * The business date is 28 November 2026 in tests.
 */
class ScreenAndReportAgreementTest extends AbstractSalesTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 28);
    private static final ReportPeriod NOVEMBER = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    @Autowired OutstandingReport outstandingReport;
    @Autowired StockLedgerReport stockLedgerReport;
    @Autowired LedgerService ledgerService;
    @Autowired ItemService itemService;
    @Autowired UnitConversionService unitConversion;

    private InvoiceResponse benchmarkSale() {
        produceBenchmarkStock();
        InvoiceResponse invoice = retailerInvoice();
        dispatch(invoice.id());
        pay("RET-A", invoice.id(), "4000.00");
        return invoice;
    }

    /** H8: the customer outstanding screen and the outstanding report, both as of the business date. */
    @Test
    void customerOutstandingMatchesTheOutstandingReport() {
        benchmarkSale();

        CustomerOutstandingResponse screen = paymentService.customerOutstanding(customerId("RET-A"), admin());
        var report = outstandingReport.build(new ReportPeriod(BUSINESS_DATE, BUSINESS_DATE), admin());

        assertThat(screen.asOf()).isEqualTo(BUSINESS_DATE);
        assertThat(screen.totalOutstanding()).isEqualByComparingTo("3875.00");
        assertThat(screen.buckets().keySet()).containsExactly("0-30", "31-60", "61-90", "90+");
        assertThat(screen.buckets().get("0-30")).isEqualByComparingTo("3875.00");
        assertThat(screen.buckets().get("31-60")).isEqualByComparingTo("0");

        ReportTable byCustomer = ReportsTestSupport.section(report, "By customer");
        List<Object> retailerA = ReportsTestSupport.row(byCustomer, "customer", "Retailer A");
        assertThat(ReportsTestSupport.number(byCustomer, retailerA, "outstanding")).isEqualByComparingTo(screen.totalOutstanding());
        for (Map.Entry<String, java.math.BigDecimal> bucket : screen.buckets().entrySet()) {
            assertThat(ReportsTestSupport.number(byCustomer, retailerA, "days" + bucket.getKey()))
                    .as(bucket.getKey()).isEqualByComparingTo(bucket.getValue());
        }

        CustomerOutstandingResponse.Invoice invoice = screen.invoices().get(0);
        ReportTable byInvoice = ReportsTestSupport.section(report, "By invoice");
        List<Object> reported = ReportsTestSupport.row(byInvoice, "invoiceNo", invoice.invoiceNo());
        assertThat(invoice.days()).isEqualTo(23L).isEqualTo(ReportsTestSupport.cell(byInvoice, reported, "days"));
        assertThat(invoice.ageingBucket()).isEqualTo("0-30").isEqualTo(ReportsTestSupport.cell(byInvoice, reported, "bucket"));
        assertThat(invoice.paid()).isEqualByComparingTo(ReportsTestSupport.number(byInvoice, reported, "paid"));
    }

    /** H5: the stock ledger screen and the stock ledger report name the same source documents. */
    @Test
    void ledgerDocumentTextMatchesTheStockLedgerReport() {
        InvoiceResponse invoice = benchmarkSale();

        assertSameDocuments("FLOUR", List.of("Bill BENCH-1 (Sample Flour Mills)", "RUN/2026-27/0001"));
        assertSameDocuments("MURUKKU200", List.of("RUN/2026-27/0001", "DC/2026-27/0001 / INV/2026-27/0001"));

        // A dispatch row also says which invoice to open
        LedgerRowResponse dispatchRow = ledger("MURUKKU200").stream()
                .filter(row -> row.documentNo().startsWith("DC/")).findFirst().orElseThrow();
        assertThat(dispatchRow.invoiceId()).isEqualTo(invoice.id());
        assertThat(ledger("FLOUR")).allSatisfy(row -> assertThat(row.invoiceId()).isNull());
    }

    private List<LedgerRowResponse> ledger(String itemCode) {
        return tx.execute(status -> ledgerService.ledger(itemCode, NOVEMBER.from(), NOVEMBER.to()));
    }

    private void assertSameDocuments(String itemCode, List<String> expected) {
        // The screen lists the seeded OPENING movement as a row; the report folds it into its opening balance
        List<String> screen = ledger(itemCode).stream()
                .filter(row -> !row.movementType().name().equals("OPENING"))
                .map(LedgerRowResponse::documentNo).toList();

        ReportTable report = stockLedgerReport.build(itemCode, NOVEMBER, admin()).sections().get(0);
        List<String> reported = new ArrayList<>();
        for (List<Object> row : report.rows().subList(1, report.rows().size())) {   // row 0 is "Opening balance"
            reported.add((String) ReportsTestSupport.cell(report, row, "document"));
        }

        assertThat(screen).as(itemCode).containsExactlyElementsOf(expected).isEqualTo(reported);
    }

    /** H2: the units offered for an item are exactly the units the converter accepts (R3.3). */
    @Test
    void offeredUnitsAreExactlyTheUnitsTheConverterAccepts() {
        Map<String, List<String>> offered = new java.util.HashMap<>();
        for (ItemResponse item : itemService.list()) {
            offered.put(item.code(), item.units());
        }

        assertThat(offered.get("OIL")).containsExactly("L", "ml");
        assertThat(offered.get("POUCH")).containsExactly("pc");
        assertThat(offered.get("FLOUR")).containsExactly("kg", "g");
        assertThat(offered.get("MURUKKU200")).containsExactly("pkt", "ctn");   // the carton is item-specific

        // Every item against every unit: offered if and only if toBase accepts it
        List<String> allUnits = jdbc.queryForList("SELECT code FROM uom", String.class);
        offered.forEach((itemCode, units) -> {
            for (String unit : allUnits) {
                boolean accepted;
                try {
                    unitConversion.toBase(itemId(itemCode), java.math.BigDecimal.ONE, unit);
                    accepted = true;
                } catch (UnitMismatchException e) {
                    accepted = false;
                }
                assertThat(units.contains(unit)).as(itemCode + " in " + unit).isEqualTo(accepted);
            }
        });
    }
}
