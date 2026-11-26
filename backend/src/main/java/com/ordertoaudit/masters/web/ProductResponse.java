package com.ordertoaudit.masters.web;

import java.math.BigDecimal;

/** A sellable product (R5.1). sellingPrice is per packet before GST. packetsPerCarton is null if not set. */
public record ProductResponse(String code, String name, BigDecimal sellingPrice, String hsnCode, BigDecimal gstRate,
                              BigDecimal packetsPerCarton) {
}
