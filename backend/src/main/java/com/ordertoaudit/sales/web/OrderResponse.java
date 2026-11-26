package com.ordertoaudit.sales.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.entity.SalesOrder;

/** One order. Quantities are in packets (R3.5). */
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
        List<Line> lines) {

    public record Line(String itemCode, String itemName, BigDecimal qty, BigDecimal approvedQty, BigDecimal rate) {
    }

    /** Call inside a transaction: it reads lazy relations. */
    public static OrderResponse from(SalesOrder order) {
        List<Line> lines = order.getLines().stream()
                .map(line -> new Line(line.getItem().getCode(), line.getItem().getName(), line.getQty(),
                        line.getApprovedQty(), line.getRate()))
                .toList();
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getClientUuid(),
                order.getCustomer().getId(), order.getCustomer().getName(), order.getSalesman().getUsername(),
                order.getOrderDate(), order.getStatus(), order.getRemarks(),
                order.getApprovedBy() == null ? null : order.getApprovedBy().getUsername(),
                order.getApprovedAt(), lines);
    }
}
