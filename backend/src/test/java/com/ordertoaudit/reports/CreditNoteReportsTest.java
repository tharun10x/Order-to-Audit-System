package com.ordertoaudit.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;
import com.ordertoaudit.reports.service.GstSummaryReport;
import com.ordertoaudit.reports.service.OutstandingReport;
import com.ordertoaudit.reports.service.ReportPeriod;
import com.ordertoaudit.reports.service.SalesRegisterReport;

/**
 * R15.3: a credit note counts negative in the period of its own date, and an invoice keeps its place in the
 * period of the invoice date (F4, F5). Also the "as of" cut-off of the outstanding report (F3).
 */
class CreditNoteReportsTest extends AbstractSalesTest {

    private static final ReportPeriod NOVEMBER = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    private static final ReportPeriod DECEMBER = new ReportPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));

    @Autowired SalesRegisterReport salesRegister;
    @Autowired GstSummaryReport gstSummary;
    @Autowired OutstandingReport outstanding;

    private InvoiceResponse invoice;   // Retailer A, 7875.00, dated 5 November

    @BeforeEach
    void anInvoice() {
        produceBenchmarkStock();
        invoice = retailerInvoice();
    }

    private ReportTable register(ReportPeriod period) {
        return ReportsTestSupport.section(salesRegister.build(period, admin()), "Invoices and credit notes");
    }

    private List<Object> gstr3b(ReportPeriod period) {
        return ReportsTestSupport.section(gstSummary.build(period, admin()), "GSTR-3B 3.1(a)").rows().get(0);
    }

    @Test
    void aCreditNoteInTheSameMonthIsANegativeRowAndCancelsTheGst() {
        dispatch(invoice.id());
        invoiceService.cancel(invoice.id(), "Wrong order", CREDIT_NOTE_DAY, admin());

        ReportTable register = register(NOVEMBER);
        assertThat(register.rows()).hasSize(2);
        List<Object> note = ReportsTestSupport.row(register, "type", "Credit note");
        assertThat(ReportsTestSupport.cell(register, note, "documentNo")).isEqualTo("CN/2026-27/0001");
        assertThat(ReportsTestSupport.cell(register, note, "invoiceNo")).isEqualTo("INV/2026-27/0001");
        assertThat(ReportsTestSupport.number(register, note, "taxable")).isEqualByComparingTo("-7500.00");
        assertThat(ReportsTestSupport.number(register, note, "total")).isEqualByComparingTo("-7875.00");
        // The invoice keeps its original figures, with its status
        List<Object> original = ReportsTestSupport.row(register, "type", "Invoice");
        assertThat(ReportsTestSupport.cell(register, original, "status")).isEqualTo("CANCELLED");
        assertThat(ReportsTestSupport.number(register, original, "total")).isEqualByComparingTo("7875.00");
        assertThat(ReportsTestSupport.total(register, "total")).isEqualByComparingTo("0.00");

        Report gst = gstSummary.build(NOVEMBER, admin());
        ReportTable cdnr = ReportsTestSupport.section(gst, "GSTR-1 CDNR");
        assertThat(cdnr.rows()).hasSize(1);
        assertThat(ReportsTestSupport.cell(cdnr, cdnr.rows().get(0), "noteType")).isEqualTo("C");
        assertThat(ReportsTestSupport.total(cdnr, "taxable")).isEqualByComparingTo("7500.00");   // as on the note
        assertThat(ReportsTestSupport.total(ReportsTestSupport.section(gst, "GSTR-1 B2B"), "taxable"))
                .isEqualByComparingTo("7500.00");
        ReportTable hsn = ReportsTestSupport.section(gst, "GSTR-1 HSN summary");
        assertThat(ReportsTestSupport.number(hsn, ReportsTestSupport.row(hsn, "hsn", "21069099"), "qty"))
                .isEqualByComparingTo("0");

        ReportTable gstr3b = ReportsTestSupport.section(gst, "GSTR-3B 3.1(a)");
        List<Object> outward = gstr3b.rows().get(0);
        assertThat(ReportsTestSupport.number(gstr3b, outward, "taxable")).isEqualByComparingTo("0.00");
        assertThat(ReportsTestSupport.number(gstr3b, outward, "cgst")).isEqualByComparingTo("0.00");
        assertThat(ReportsTestSupport.number(gstr3b, outward, "sgst")).isEqualByComparingTo("0.00");

        assertThat(ReportsTestSupport.section(outstanding.build(NOVEMBER, admin()), "By customer").rows()).isEmpty();
    }

    @Test
    void aCreditNoteInALaterMonthReducesThatMonthOnly() {
        invoiceService.cancel(invoice.id(), "Returned in December", LocalDate.of(2026, 12, 3), admin());

        // November still shows the sale, as it was reported then
        assertThat(ReportsTestSupport.total(register(NOVEMBER), "total")).isEqualByComparingTo("7875.00");
        // December carries only the credit note, negative
        ReportTable december = register(DECEMBER);
        assertThat(december.rows()).singleElement()
                .satisfies(row -> assertThat(row.get(0)).isEqualTo("Credit note"));
        assertThat(ReportsTestSupport.total(december, "total")).isEqualByComparingTo("-7875.00");
        List<Object> decemberGst = gstr3b(DECEMBER);
        assertThat((BigDecimal) decemberGst.get(1)).isEqualByComparingTo("-7500.00");

        // Owed at the end of November, not at the end of December
        assertThat(ReportsTestSupport.total(ReportsTestSupport.section(outstanding.build(NOVEMBER, admin()), "By customer"),
                "outstanding")).isEqualByComparingTo("7875.00");
        assertThat(ReportsTestSupport.section(outstanding.build(DECEMBER, admin()), "By customer").rows()).isEmpty();
    }

    @Test
    void outstandingIsAsOfTheLastDayOfThePeriod() {
        pay("RET-A", invoice.id(), "4000.00");   // received 10 November

        assertThat(outstandingOn(LocalDate.of(2026, 11, 4))).isEmpty();   // before the invoice
        assertThat(outstandingOn(LocalDate.of(2026, 11, 9))).containsExactly(new BigDecimal("7875.00"));
        assertThat(outstandingOn(LocalDate.of(2026, 11, 10))).containsExactly(new BigDecimal("3875.00"));

        ReportTable byInvoice = ReportsTestSupport.section(outstanding.build(
                new ReportPeriod(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 15)), admin()), "By invoice");
        List<Object> row = byInvoice.rows().get(0);
        assertThat(ReportsTestSupport.cell(byInvoice, row, "days")).isEqualTo(71L);   // 5 Nov to 15 Jan
        assertThat(ReportsTestSupport.cell(byInvoice, row, "bucket")).isEqualTo("61-90");
    }

    private List<BigDecimal> outstandingOn(LocalDate day) {
        ReportTable byInvoice = ReportsTestSupport.section(
                outstanding.build(new ReportPeriod(day, day), admin()), "By invoice");
        return byInvoice.rows().stream().map(row -> ReportsTestSupport.number(byInvoice, row, "outstanding")).toList();
    }
}
