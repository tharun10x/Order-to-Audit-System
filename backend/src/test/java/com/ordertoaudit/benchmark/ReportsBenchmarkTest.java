package com.ordertoaudit.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.common.error.OrderShortageException;
import com.ordertoaudit.production.entity.OverheadType;
import com.ordertoaudit.production.web.OverheadRequest;
import com.ordertoaudit.reports.ReportsTestSupport;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;
import com.ordertoaudit.reports.service.CollectionsReport;
import com.ordertoaudit.reports.service.DashboardService;
import com.ordertoaudit.reports.service.GstSummaryReport;
import com.ordertoaudit.reports.service.MySalesReport;
import com.ordertoaudit.reports.service.OutstandingReport;
import com.ordertoaudit.reports.service.ProductionReport;
import com.ordertoaudit.reports.service.PurchaseRegisterReport;
import com.ordertoaudit.reports.service.ReportPeriod;
import com.ordertoaudit.reports.service.SalesRegisterReport;
import com.ordertoaudit.reports.service.StockSummaryReport;
import com.ordertoaudit.reports.web.DashboardResponse;
import com.ordertoaudit.sales.entity.OrderStatus;

/**
 * Problem statement section 5, all steps, then every report for November 2026 against business-rules.md
 * section 16. The business clock is fixed at 28 November 2026 for tests (F1), so "today" is in that month.
 */
class ReportsBenchmarkTest extends AbstractSalesTest {

    private static final ReportPeriod NOVEMBER = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    @Autowired SalesRegisterReport salesRegister;
    @Autowired GstSummaryReport gstSummary;
    @Autowired PurchaseRegisterReport purchaseRegister;
    @Autowired StockSummaryReport stockSummary;
    @Autowired ProductionReport productionReport;
    @Autowired CollectionsReport collections;
    @Autowired OutstandingReport outstanding;
    @Autowired MySalesReport mySales;
    @Autowired DashboardService dashboard;

    /** Steps 1 to 8: purchase, run, Retailer A billed and dispatched, Distributor B short, payment, overheads. */
    @BeforeEach
    void runTheWholeBenchmark() {
        produceBenchmarkStock();
        InvoiceResponse retailerA = retailerInvoice();
        dispatch(retailerA.id());
        Long distributorB = order("salesman_b", "DIST-B", "120").id();
        assertThatThrownBy(() -> orderService.approve(distributorB, null, admin()))
                .isInstanceOf(OrderShortageException.class);
        pay("RET-A", retailerA.id(), "4000.00");
        Long runId = jdbc.queryForObject("SELECT id FROM production_run", Long.class);
        productionService.addOverhead(runId, new OverheadRequest(OverheadType.LABOUR, new BigDecimal("1200"), null), admin());
        productionService.addOverhead(runId, new OverheadRequest(OverheadType.POWER_GAS, new BigDecimal("600"), null), admin());
    }

    @Test
    void salesRegisterHasTheRetailerAInvoice() {
        Report report = salesRegister.build(NOVEMBER, admin());

        ReportTable register = ReportsTestSupport.section(report, "Invoices and credit notes");
        assertThat(register.rows()).hasSize(1);
        assertThat(ReportsTestSupport.cell(register, register.rows().get(0), "documentNo")).isEqualTo("INV/2026-27/0001");
        assertThat(ReportsTestSupport.total(register, "total")).isEqualByComparingTo("7875.00");

        ReportTable products = ReportsTestSupport.section(report, "Product-wise sales");
        List<Object> murukku = ReportsTestSupport.row(products, "itemCode", "MURUKKU200");
        assertThat(ReportsTestSupport.number(products, murukku, "qty")).isEqualByComparingTo("150");
        assertThat(ReportsTestSupport.number(products, murukku, "taxable")).isEqualByComparingTo("7500.00");

        ReportTable customers = ReportsTestSupport.section(report, "Customer-wise sales");
        assertThat(ReportsTestSupport.number(customers, ReportsTestSupport.row(customers, "customer", "Retailer A"), "total"))
                .isEqualByComparingTo("7875.00");
    }

    @Test
    void gstSummaryMatchesTheInvoice() {
        Report report = gstSummary.build(NOVEMBER, admin());

        ReportTable b2b = ReportsTestSupport.section(report, "GSTR-1 B2B");
        assertThat(b2b.rows()).hasSize(1);
        assertThat(ReportsTestSupport.total(b2b, "taxable")).isEqualByComparingTo("7500.00");
        assertThat(ReportsTestSupport.total(b2b, "cgst")).isEqualByComparingTo("187.50");
        assertThat(ReportsTestSupport.total(b2b, "sgst")).isEqualByComparingTo("187.50");
        assertThat(ReportsTestSupport.total(b2b, "igst")).isEqualByComparingTo("0.00");
        assertThat(ReportsTestSupport.section(report, "GSTR-1 B2CS").rows()).isEmpty();
        assertThat(ReportsTestSupport.section(report, "GSTR-1 CDNR").rows()).isEmpty();

        ReportTable hsn = ReportsTestSupport.section(report, "GSTR-1 HSN summary");
        List<Object> murukku = ReportsTestSupport.row(hsn, "hsn", "21069099");
        assertThat(ReportsTestSupport.number(hsn, murukku, "qty")).isEqualByComparingTo("150");
        assertThat(ReportsTestSupport.cell(hsn, murukku, "uqc")).isEqualTo("PAC");
        assertThat(ReportsTestSupport.cell(hsn, murukku, "type")).isEqualTo("B2B");
        assertThat(ReportsTestSupport.number(hsn, murukku, "totalValue")).isEqualByComparingTo("7875.00");

        // 3.1(a) = B2B + B2CS - CDNR
        ReportTable gstr3b = ReportsTestSupport.section(report, "GSTR-3B 3.1(a)");
        List<Object> outward = gstr3b.rows().get(0);
        assertThat(ReportsTestSupport.number(gstr3b, outward, "taxable")).isEqualByComparingTo("7500.00");
        assertThat(ReportsTestSupport.number(gstr3b, outward, "cgst")).isEqualByComparingTo("187.50");
        assertThat(ReportsTestSupport.number(gstr3b, outward, "sgst")).isEqualByComparingTo("187.50");
        assertThat(ReportsTestSupport.number(gstr3b, outward, "igst")).isEqualByComparingTo("0.00");
    }

    @Test
    void purchaseRegisterHasTheSixtyKgOfFlour() {
        ReportTable bills = purchaseRegister.build(NOVEMBER, admin()).sections().get(0);

        List<Object> flour = ReportsTestSupport.row(bills, "itemCode", "FLOUR");
        assertThat(ReportsTestSupport.number(bills, flour, "qty")).isEqualByComparingTo("60");
        assertThat(ReportsTestSupport.cell(bills, flour, "unit")).isEqualTo("kg");
        assertThat(ReportsTestSupport.number(bills, flour, "rate")).isEqualByComparingTo("45");
        assertThat(ReportsTestSupport.total(bills, "amount")).isEqualByComparingTo("2700.00");
    }

    @Test
    void stockSummaryMatchesTheClosingCheck() {
        Report report = stockSummary.build(NOVEMBER, admin());

        assertStock(report, "Raw materials", "FLOUR", "100", "60", "40", "120");
        assertClosing(report, "Raw materials", "OIL", "19.5");
        assertClosing(report, "Raw materials", "SPICE", "3.8");
        assertClosing(report, "Packaging", "POUCH", "306");
        assertClosing(report, "Packaging", "CARTON", "21");
        assertStock(report, "Finished goods", "MURUKKU200", "50", "188", "150", "88");

        // R6.4 for every item, from the figures as printed
        for (ReportTable section : report.sections()) {
            for (List<Object> row : section.rows()) {
                BigDecimal expected = ReportsTestSupport.number(section, row, "opening")
                        .add(ReportsTestSupport.number(section, row, "receipts"))
                        .subtract(ReportsTestSupport.number(section, row, "issues"))
                        .add(ReportsTestSupport.number(section, row, "adjustments"));
                assertThat(ReportsTestSupport.number(section, row, "closing")).as(row.get(0).toString())
                        .isEqualByComparingTo(expected);
            }
        }
        // 5555.00 - 3501.05 dispatched
        assertThat(ReportsTestSupport.total(ReportsTestSupport.section(report, "Finished goods"), "closingValue"))
                .isEqualByComparingTo("2053.95");
    }

    private void assertStock(Report report, String section, String itemCode, String opening, String receipts,
                             String issues, String closing) {
        ReportTable table = ReportsTestSupport.section(report, section);
        List<Object> row = ReportsTestSupport.row(table, "itemCode", itemCode);
        assertThat(ReportsTestSupport.number(table, row, "opening")).as(itemCode + " opening").isEqualByComparingTo(opening);
        assertThat(ReportsTestSupport.number(table, row, "receipts")).as(itemCode + " receipts").isEqualByComparingTo(receipts);
        assertThat(ReportsTestSupport.number(table, row, "issues")).as(itemCode + " issues").isEqualByComparingTo(issues);
        assertThat(ReportsTestSupport.number(table, row, "closing")).as(itemCode + " closing").isEqualByComparingTo(closing);
    }

    private void assertClosing(Report report, String section, String itemCode, String closing) {
        ReportTable table = ReportsTestSupport.section(report, section);
        assertThat(ReportsTestSupport.number(table, ReportsTestSupport.row(table, "itemCode", itemCode), "closing"))
                .as(itemCode + " closing").isEqualByComparingTo(closing);
    }

    @Test
    void productionReportShowsTheFlagsAndCostPerPacket() {
        Report report = productionReport.build(NOVEMBER, admin());

        ReportTable runs = ReportsTestSupport.section(report, "Runs");
        List<Object> run = ReportsTestSupport.row(runs, "runNo", "RUN/2026-27/0001");
        assertThat(ReportsTestSupport.number(runs, run, "yieldVariance")).isEqualByComparingTo("-6.00");
        assertThat(ReportsTestSupport.cell(runs, run, "yieldFlag")).isEqualTo(ProductionReport.FLAG);
        assertThat(ReportsTestSupport.number(runs, run, "costPerPacket")).isEqualByComparingTo("31.14");
        assertThat(ReportsTestSupport.number(runs, run, "totalCost")).isEqualByComparingTo("5855.00");

        ReportTable lines = ReportsTestSupport.section(report, "RUN/2026-27/0001 consumption");
        List<Object> oil = ReportsTestSupport.row(lines, "itemCode", "OIL");
        assertThat(ReportsTestSupport.number(lines, oil, "variance")).isEqualByComparingTo("5.00");
        assertThat(ReportsTestSupport.cell(lines, oil, "flag")).isEqualTo(ProductionReport.FLAG);
        List<Object> pouch = ReportsTestSupport.row(lines, "itemCode", "POUCH");
        assertThat(ReportsTestSupport.number(lines, pouch, "variance")).isEqualByComparingTo("3.19");
        assertThat(ReportsTestSupport.cell(lines, pouch, "flag")).isEqualTo(ProductionReport.FLAG);
        assertThat(ReportsTestSupport.cell(lines, ReportsTestSupport.row(lines, "itemCode", "FLOUR"), "flag")).isNull();
    }

    @Test
    void collectionsShowTheFourThousand() {
        ReportTable payments = collections.build(NOVEMBER, admin()).sections().get(0);

        assertThat(payments.rows()).hasSize(1);
        assertThat(ReportsTestSupport.cell(payments, payments.rows().get(0), "allocatedTo"))
                .isEqualTo("INV/2026-27/0001: 4000.00");
        assertThat(ReportsTestSupport.total(payments, "amount")).isEqualByComparingTo("4000.00");
    }

    @Test
    void retailerAOwes3875AllInTheFirstAgeingBucket() {
        Report report = outstanding.build(NOVEMBER, admin());

        assertThat(report.periodLabel()).isEqualTo("As of 30-Nov-2026");
        ReportTable customers = ReportsTestSupport.section(report, "By customer");
        assertThat(customers.rows()).hasSize(1);
        List<Object> retailerA = ReportsTestSupport.row(customers, "customer", "Retailer A");
        assertThat(ReportsTestSupport.number(customers, retailerA, "outstanding")).isEqualByComparingTo("3875.00");
        assertThat(ReportsTestSupport.number(customers, retailerA, "days0-30")).isEqualByComparingTo("3875.00");
        for (String bucket : new String[] {"days31-60", "days61-90", "days90+"}) {
            assertThat(ReportsTestSupport.number(customers, retailerA, bucket)).as(bucket).isEqualByComparingTo("0");
        }
        ReportTable invoices = ReportsTestSupport.section(report, "By invoice");
        assertThat(ReportsTestSupport.cell(invoices, invoices.rows().get(0), "days")).isEqualTo(25L);   // 5 to 30 Nov
    }

    @Test
    void eachSalesmanSeesOnlyTheirOwnCustomers() {
        ReportTable salesmanA = mySales.build(NOVEMBER, user("salesman_a")).sections().get(0);
        assertThat(salesmanA.rows()).hasSize(1);
        List<Object> retailerA = ReportsTestSupport.row(salesmanA, "customerCode", "RET-A");
        assertThat(ReportsTestSupport.cell(salesmanA, retailerA, "orders")).isEqualTo(1L);
        assertThat(ReportsTestSupport.number(salesmanA, retailerA, "invoiced")).isEqualByComparingTo("7875.00");
        assertThat(ReportsTestSupport.number(salesmanA, retailerA, "outstanding")).isEqualByComparingTo("3875.00");

        ReportTable salesmanB = mySales.build(NOVEMBER, user("salesman_b")).sections().get(0);
        assertThat(salesmanB.rows()).hasSize(1);
        List<Object> distributorB = ReportsTestSupport.row(salesmanB, "customerCode", "DIST-B");
        assertThat(ReportsTestSupport.cell(salesmanB, distributorB, "orders")).isEqualTo(1L);
        assertThat(ReportsTestSupport.number(salesmanB, distributorB, "invoiced")).isEqualByComparingTo("0");
        assertThat(ReportsTestSupport.number(salesmanB, distributorB, "outstanding")).isEqualByComparingTo("0");
    }

    @Test
    void dashboardShowsTheAlertsCostAndDues() {
        jdbc.update("UPDATE item SET reorder_level = 400 WHERE code = 'POUCH'");
        try {
            DashboardResponse summary = dashboard.dashboard();

            assertThat(summary.businessDate()).isEqualTo(LocalDate.of(2026, 11, 28));
            // Both benchmark orders were placed "today" by the business clock
            assertThat(summary.todaysOrders().get(OrderStatus.DISPATCHED)).isEqualTo(1L);
            assertThat(summary.todaysOrders().get(OrderStatus.SUBMITTED)).isEqualTo(1L);
            assertThat(summary.todaysOrders().get(OrderStatus.APPROVED)).isZero();

            assertThat(summary.varianceAlerts()).extracting(DashboardResponse.VarianceAlert::subject)
                    .containsExactlyInAnyOrder("OIL", "POUCH", "YIELD");
            assertThat(summary.costPerPacket()).singleElement().satisfies(cost -> {
                assertThat(cost.costPerPacket()).isEqualByComparingTo("31.1436");
                assertThat(cost.costPerPacketRounded()).isEqualByComparingTo("31.14");
            });
            assertThat(summary.totalOutstanding()).isEqualByComparingTo("3875.00");
            assertThat(summary.topOutstanding()).singleElement()
                    .satisfies(due -> assertThat(due.customerName()).isEqualTo("Retailer A"));

            // 306 pouches are below the reorder level of 400; flour has none set
            assertThat(summary.stockLevels()).filteredOn(level -> level.itemCode().equals("POUCH"))
                    .singleElement().satisfies(level -> assertThat(level.lowStock()).isTrue());
            assertThat(summary.stockLevels()).filteredOn(level -> level.itemCode().equals("FLOUR"))
                    .singleElement().satisfies(level -> assertThat(level.lowStock()).isFalse());
        } finally {
            jdbc.update("UPDATE item SET reorder_level = NULL WHERE code = 'POUCH'");
        }
    }
}
