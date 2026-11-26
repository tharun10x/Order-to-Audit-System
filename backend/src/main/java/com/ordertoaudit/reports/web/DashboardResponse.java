package com.ordertoaudit.reports.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ordertoaudit.sales.entity.OrderStatus;

/**
 * GET /api/dashboard: the owner's one-screen summary. businessDate is "today" by the business clock;
 * todaysOrders lists every status, 0 when there are none.
 */
public record DashboardResponse(
        LocalDate businessDate,
        Map<OrderStatus, Long> todaysOrders,
        List<StockLevel> stockLevels,
        List<VarianceAlert> varianceAlerts,
        List<ProductCost> costPerPacket,
        BigDecimal totalOutstanding,
        List<CustomerDue> topOutstanding) {

    /** lowStock is true when a reorder level is set and the stock is below it. */
    public record StockLevel(String itemCode, String itemName, String kind, String unit, BigDecimal qtyOnHand,
                             BigDecimal reorderLevel, boolean lowStock) {
    }

    /** subject is an item code, or "YIELD" for the run's output. */
    public record VarianceAlert(String runNo, LocalDate runDate, String productCode, String subject,
                                BigDecimal variancePct) {
    }

    /** From the latest POSTED run of each product, overheads included (R13.5). */
    public record ProductCost(String productCode, String productName, String runNo, LocalDate runDate,
                              BigDecimal costPerPacket, BigDecimal costPerPacketRounded) {
    }

    public record CustomerDue(Long customerId, String customerName, BigDecimal outstanding) {
    }
}
