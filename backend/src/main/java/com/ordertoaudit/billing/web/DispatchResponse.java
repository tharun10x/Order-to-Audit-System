package com.ordertoaudit.billing.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DispatchResponse(
        Long id,
        String dispatchNo,
        Long invoiceId,
        String invoiceNo,
        Long orderId,
        LocalDate dispatchDate,
        String vehicleNo,
        List<Line> lines) {

    public record Line(String itemCode, BigDecimal qty) {
    }
}
