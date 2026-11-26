package com.ordertoaudit.reports.model;

/** key is for JSON clients, header is what people read. */
public record Column(String key, String header, ColumnType type) {
}
