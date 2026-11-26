package com.ordertoaudit.reports.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One table of a report. Each row is a list of values in column order: String (TEXT), LocalDate (DATE),
 * BigDecimal (QTY, RATE, MONEY, PERCENT), Long (COUNT), or null for an empty cell.
 * totals is null when the table has no totals row.
 */
public record ReportTable(String title, List<Column> columns, List<List<Object>> rows, List<Object> totals) {

    public static Builder builder(String title) {
        return new Builder(title);
    }

    /** Builds a table column by column, then row by row. Rows are checked against the columns as they are added. */
    public static final class Builder {

        private final String title;
        private final List<Column> columns = new ArrayList<>();
        private final List<List<Object>> rows = new ArrayList<>();
        private List<Object> totals;

        private Builder(String title) {
            this.title = title;
        }

        public Builder column(String key, String header, ColumnType type) {
            columns.add(new Column(key, header, type));
            return this;
        }

        public Builder row(Object... values) {
            rows.add(checked(values));
            return this;
        }

        public Builder totals(Object... values) {
            totals = checked(values);
            return this;
        }

        public ReportTable build() {
            return new ReportTable(title, List.copyOf(columns), List.copyOf(rows), totals);
        }

        /** Arrays.asList, not List.of: report cells can be null. */
        private List<Object> checked(Object[] values) {
            if (values.length != columns.size()) {
                throw new IllegalArgumentException(title + ": " + values.length + " values for " + columns.size()
                        + " columns");
            }
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && !fits(columns.get(i).type(), values[i])) {
                    throw new IllegalArgumentException(title + ": column " + columns.get(i).key() + " is "
                            + columns.get(i).type() + " but got " + values[i].getClass().getSimpleName());
                }
            }
            return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
        }

        private static boolean fits(ColumnType type, Object value) {
            return switch (type) {
                case TEXT -> value instanceof String;
                case DATE -> value instanceof LocalDate;
                case COUNT -> value instanceof Long;
                case QTY, RATE, MONEY, PERCENT -> value instanceof BigDecimal;
            };
        }
    }
}
