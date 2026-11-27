package com.ordertoaudit.sales.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.entity.SalesOrder;
import com.ordertoaudit.sales.entity.SalesOrderLine;
import com.ordertoaudit.sales.repository.SalesOrderRepository;

/**
 * One order. Quantities are in packets (R3.5). taxableValue is before GST: per line the approved quantity once
 * approved (otherwise the ordered one) x the rate, rounded per line (R10.4), then added up. invoiceId and invoiceNo
 * are null until the order is invoiced.
 */
public record OrderResponse(
        Long id,
        String orderNo,
        UUID clientUuid,
        Long customerId,
        String customerName,
        String salesman,
        LocalDate orderDate,
        OrderStatus status,
        String remarks,
        String approvedBy,
        Instant approvedAt,
        BigDecimal taxableValue,
        Long invoiceId,
        String invoiceNo,
        List<Line> lines) {

    public record Line(String itemCode, String itemName, BigDecimal qty, BigDecimal approvedQty, BigDecimal rate,
                       BigDecimal taxableValue) {
    }

    /** Call inside a transaction: it reads lazy relations. */
    public static OrderResponse from(SalesOrder order, SalesOrderRepository.OrderInvoice invoice) {
        List<Line> lines = order.getLines().stream()
                .map(line -> new Line(line.getItem().getCode(), line.getItem().getName(), line.getQty(),
                        line.getApprovedQty(), line.getRate(), taxable(line)))
                .toList();
        BigDecimal taxableValue = lines.stream().map(Line::taxableValue)
                .reduce(Money.money(BigDecimal.ZERO), BigDecimal::add);
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getClientUuid(),
                order.getCustomer().getId(), order.getCustomer().getName(), order.getSalesman().getUsername(),
                order.getOrderDate(), order.getStatus(), order.getRemarks(),
                order.getApprovedBy() == null ? null : order.getApprovedBy().getUsername(),
                order.getApprovedAt(), taxableValue,
                invoice == null ? null : invoice.getInvoiceId(), invoice == null ? null : invoice.getInvoiceNo(), lines);
    }

    /** The same rounding as the invoice line (R10.4), so an approved order shows what its invoice will say. */
    private static BigDecimal taxable(SalesOrderLine line) {
        BigDecimal qty = line.getApprovedQty() != null ? line.getApprovedQty() : line.getQty();
        return Money.money(qty.multiply(line.getRate()));
    }
}
