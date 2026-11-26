package com.ordertoaudit.sales.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.service.CreateOrderResult;
import com.ordertoaudit.sales.service.SalesOrderService;
import com.ordertoaudit.security.CurrentUserProvider;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class SalesOrderController {

    private final SalesOrderService orderService;
    private final CurrentUserProvider currentUser;

    public SalesOrderController(SalesOrderService orderService, CurrentUserProvider currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    /** 201 for a new order, 200 when this clientUuid was already saved (R9.7). */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResult result = orderService.create(request, currentUser.current());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return orderService.list(status, currentUser.current());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id, currentUser.current());
    }

    @GetMapping("/{id}/stock-check")
    public StockCheckResponse stockCheck(@PathVariable Long id) {
        return orderService.stockCheck(id, currentUser.current());
    }

    @PutMapping("/{id}/lines")
    public OrderResponse updateLines(@PathVariable Long id, @Valid @RequestBody UpdateOrderLinesRequest request) {
        return orderService.updateLines(id, request, currentUser.current());
    }

    @PostMapping("/{id}/approve")
    public OrderResponse approve(@PathVariable Long id,
                                 @Valid @RequestBody(required = false) ApproveOrderRequest request) {
        return orderService.approve(id, request, currentUser.current());
    }

    @PostMapping("/{id}/reject")
    public OrderResponse reject(@PathVariable Long id, @Valid @RequestBody RejectOrderRequest request) {
        return orderService.reject(id, request.reason(), currentUser.current());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(id, currentUser.current());
    }
}
