package com.ordertoaudit.reports.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Function;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.reports.export.ReportFormat;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.service.CollectionsReport;
import com.ordertoaudit.reports.service.GstSummaryReport;
import com.ordertoaudit.reports.service.MySalesReport;
import com.ordertoaudit.reports.service.OutstandingReport;
import com.ordertoaudit.reports.service.ProductionReport;
import com.ordertoaudit.reports.service.PurchaseRegisterReport;
import com.ordertoaudit.reports.service.ReportPeriod;
import com.ordertoaudit.reports.service.SalesRegisterReport;
import com.ordertoaudit.reports.service.StockLedgerReport;
import com.ordertoaudit.reports.service.StockSummaryReport;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

/**
 * GET /api/reports/{report}?from=&to=&format=json|csv|xlsx|pdf (R15). Both dates are inclusive and default to
 * the current month. One method per report, so each declares its own roles (R2.1).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final SalesRegisterReport salesRegister;
    private final GstSummaryReport gstSummary;
    private final PurchaseRegisterReport purchaseRegister;
    private final StockSummaryReport stockSummary;
    private final StockLedgerReport stockLedger;
    private final ProductionReport productionReport;
    private final CollectionsReport collections;
    private final OutstandingReport outstanding;
    private final MySalesReport mySales;
    private final ReportResponses responses;
    private final CurrentUserProvider currentUser;
    private final Clock businessClock;

    public ReportController(SalesRegisterReport salesRegister, GstSummaryReport gstSummary,
                            PurchaseRegisterReport purchaseRegister, StockSummaryReport stockSummary,
                            StockLedgerReport stockLedger, ProductionReport productionReport,
                            CollectionsReport collections, OutstandingReport outstanding, MySalesReport mySales,
                            ReportResponses responses, CurrentUserProvider currentUser, Clock businessClock) {
        this.salesRegister = salesRegister;
        this.gstSummary = gstSummary;
        this.purchaseRegister = purchaseRegister;
        this.stockSummary = stockSummary;
        this.stockLedger = stockLedger;
        this.productionReport = productionReport;
        this.collections = collections;
        this.outstanding = outstanding;
        this.mySales = mySales;
        this.responses = responses;
        this.currentUser = currentUser;
        this.businessClock = businessClock;
    }

    /** The request parameters every report takes. */
    public record Query(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            String format) {
    }

    @PreAuthorize(Roles.REPORTS_READ)
    @GetMapping("/sales-register")
    public ResponseEntity<Object> salesRegister(Query query) {
        return report(query, period -> salesRegister.build(period, currentUser.current()));
    }

    @PreAuthorize(Roles.REPORTS_READ)
    @GetMapping("/gst-summary")
    public ResponseEntity<Object> gstSummary(Query query) {
        return report(query, period -> gstSummary.build(period, currentUser.current()));
    }

    @PreAuthorize(Roles.REPORTS_READ)
    @GetMapping("/purchase-register")
    public ResponseEntity<Object> purchaseRegister(Query query) {
        return report(query, period -> purchaseRegister.build(period, currentUser.current()));
    }

    @PreAuthorize(Roles.OPERATIONS_READ)
    @GetMapping("/stock-summary")
    public ResponseEntity<Object> stockSummary(Query query) {
        return report(query, period -> stockSummary.build(period, currentUser.current()));
    }

    @PreAuthorize(Roles.OPERATIONS_READ)
    @GetMapping("/stock-ledger")
    public ResponseEntity<Object> stockLedger(Query query, @RequestParam String itemCode) {
        return report(query, period -> stockLedger.build(itemCode, period, currentUser.current()));
    }

    @PreAuthorize(Roles.OPERATIONS_READ)
    @GetMapping("/production-report")
    public ResponseEntity<Object> productionReport(Query query) {
        return report(query, period -> productionReport.build(period, currentUser.current()));
    }

    @PreAuthorize(Roles.REPORTS_READ)
    @GetMapping("/collections")
    public ResponseEntity<Object> collections(Query query) {
        return report(query, period -> collections.build(period, currentUser.current()));
    }

    @PreAuthorize(Roles.REPORTS_READ)
    @GetMapping("/outstanding")
    public ResponseEntity<Object> outstanding(Query query) {
        return report(query, period -> outstanding.build(period, currentUser.current()));
    }

    /** Brief 4.1: the logged-in salesman's own customers only. */
    @PreAuthorize(Roles.SALESMAN_ONLY)
    @GetMapping("/my-sales")
    public ResponseEntity<Object> mySales(Query query) {
        return report(query, period -> mySales.build(period, currentUser.current()));
    }

    /** The format is checked before any work is done, so a bad one is a quick 400. */
    private ResponseEntity<Object> report(Query query, Function<ReportPeriod, Report> build) {
        ReportFormat format = ReportFormat.parse(query.format() == null ? "json" : query.format());
        ReportPeriod period = ReportPeriod.resolve(query.from(), query.to(), businessClock);
        return responses.respond(build.apply(period), format);
    }
}
