package com.ordertoaudit.sales.service;

import com.ordertoaudit.sales.web.OrderResponse;

/** created is false when the same clientUuid had already been saved (R9.7). */
public record CreateOrderResult(OrderResponse order, boolean created) {
}
