package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.production.entity.ProductionRun;
import com.ordertoaudit.payments.service.OutstandingCalculator;
import com.ordertoaudit.production.entity.RunStatus;
import com.ordertoaudit.production.repository.ProductionRunRepository;
import com.ordertoaudit.production.service.RunReportService;
import com.ordertoaudit.production.web.RunResponse;
import com.ordertoaudit.reports.web.DashboardResponse;
import com.ordertoaudit.sales.entity.OrderStatus;

/** The owner dashboard (R2, brief section 3). Read-only, all figures derived on request. */
@Service
public class DashboardService {

    /** Variance alerts look back this many days from today. */
    static final int ALERT_DAYS = 30;
    private static final int TOP_CUSTOMERS = 5;

    private static final String ORDERS_TODAY = """
            SELECT status, count(*) AS orders FROM sales_order WHERE order_date = :today GROUP BY status""";

    private static final String STOCK_LEVELS = """
            SELECT b.code AS item_code, b.name AS item_name, b.kind, u.code AS unit, b.qty_on_hand, i.reorder_level
            FROM stock_balance b
            JOIN item i ON i.id = b.item_id
            JOIN uom u ON u.id = i.base_uom_id
            WHERE i.active
            ORDER BY b.kind, b.code""";

    private record StatusCount(String status, Long orders) {
    }

    private record Stock(String itemCode, String itemName, String kind, String unit, BigDecimal qtyOnHand,
                         BigDecimal reorderLevel) {
    }

    private final JdbcClient jdbc;
    private final ProductionRunRepository runRepository;
    private final RunReportService runReportService;
    private final OutstandingCalculator outstandingCalculator;
    private final Clock businessClock;

    public DashboardService(JdbcClient jdbc, ProductionRunRepository runRepository, RunReportService runReportService,
                            OutstandingCalculator outstandingCalculator, Clock businessClock) {
        this.jdbc = jdbc;
        this.runRepository = runRepository;
        this.runReportService = runReportService;
        this.outstandingCalculator = outstandingCalculator;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse dashboard() {
        LocalDate today = LocalDate.now(businessClock);
        List<OutstandingCalculator.InvoiceDue> dues = outstandingCalculator.asOf(today);
        BigDecimal totalOutstanding = dues.stream().map(OutstandingCalculator.InvoiceDue::outstanding)
                .reduce(TaxAmounts.zero(), BigDecimal::add);
        return new DashboardResponse(today, ordersToday(today), stockLevels(), varianceAlerts(today),
                latestCostPerPacket(), totalOutstanding, topCustomers(dues));
    }

    private Map<OrderStatus, Long> ordersToday(LocalDate today) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.sql(ORDERS_TODAY).param("today", today).query(StatusCount.class).list()
                .forEach(count -> counts.put(OrderStatus.valueOf(count.status()), count.orders()));
        return counts;
    }

    private List<DashboardResponse.StockLevel> stockLevels() {
        return jdbc.sql(STOCK_LEVELS).query(Stock.class).list().stream()
                .map(s -> new DashboardResponse.StockLevel(s.itemCode(), s.itemName(), s.kind(), s.unit(),
                        s.qtyOnHand(), s.reorderLevel(),
                        s.reorderLevel() != null && s.qtyOnHand().compareTo(s.reorderLevel()) < 0))
                .toList();
    }

    /** R8.9: every flagged line and flagged yield of the runs posted in the last 30 days. */
    private List<DashboardResponse.VarianceAlert> varianceAlerts(LocalDate today) {
        List<DashboardResponse.VarianceAlert> alerts = new ArrayList<>();
        for (ProductionRun run : runRepository.findByStatusAndRunDateBetweenOrderByRunDateAscIdAsc(
                RunStatus.POSTED, today.minusDays(ALERT_DAYS), today)) {
            RunResponse report = runReportService.build(run);
            for (RunResponse.Line line : report.lines()) {
                if (line.flagged()) {
                    alerts.add(new DashboardResponse.VarianceAlert(report.runNo(), report.runDate(),
                            report.productCode(), line.itemCode(), line.variancePct()));
                }
            }
            if (report.yieldFlagged()) {
                alerts.add(new DashboardResponse.VarianceAlert(report.runNo(), report.runDate(),
                        report.productCode(), "YIELD", report.yieldVariancePct()));
            }
        }
        return alerts;
    }

    private List<DashboardResponse.ProductCost> latestCostPerPacket() {
        List<DashboardResponse.ProductCost> costs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProductionRun run : runRepository.findByStatusOrderByRunDateDescIdDesc(RunStatus.POSTED)) {
            if (!seen.add(run.getBom().getProduct().getCode())) {
                continue;   // an older run of a product already listed
            }
            RunResponse report = runReportService.build(run);
            costs.add(new DashboardResponse.ProductCost(report.productCode(), report.productName(), report.runNo(),
                    report.runDate(), report.costing().costPerPacket(), report.costing().costPerPacketRounded()));
        }
        return costs;
    }

    private List<DashboardResponse.CustomerDue> topCustomers(List<OutstandingCalculator.InvoiceDue> dues) {
        Map<Long, DashboardResponse.CustomerDue> byCustomer = new LinkedHashMap<>();
        for (OutstandingCalculator.InvoiceDue due : dues) {
            byCustomer.merge(due.customerId(),
                    new DashboardResponse.CustomerDue(due.customerId(), due.customerName(), due.outstanding()),
                    (a, b) -> new DashboardResponse.CustomerDue(a.customerId(), a.customerName(),
                            a.outstanding().add(b.outstanding())));
        }
        return byCustomer.values().stream()
                .sorted(Comparator.comparing(DashboardResponse.CustomerDue::outstanding).reversed()
                        .thenComparing(DashboardResponse.CustomerDue::customerName))
                .limit(TOP_CUSTOMERS)
                .toList();
    }
}
