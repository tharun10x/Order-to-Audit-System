package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * R6.4, R15.2: per item, opening + receipts - issues/sales +/- adjustments = closing, grouped raw /
 * packaging / finished goods. Everything is summed from the stock ledger in one query.
 *
 * Opening = every movement before "from", plus every OPENING movement whatever its date: the seeded
 * opening stock is dated 1 November 00:00 and must not show up as a November receipt.
 * Receipts = RECEIPT + PRODUCTION_OUTPUT + RETURN; issues/sales = ISSUE + SALE;
 * adjustments = ADJUSTMENT + REVERSAL (either sign).
 */
@Service
public class StockSummaryReport {

    public static final String CODE = "stock-summary";

    /** Shared with the stock ledger report, so both start from the same opening balance. */
    static final String OPENING = "(m.movement_type = 'OPENING' OR m.movement_date < :start)";
    private static final String IN_PERIOD =
            "m.movement_type <> 'OPENING' AND m.movement_date >= :start AND m.movement_date < :end";
    private static final String CLOSING = "(m.movement_type = 'OPENING' OR m.movement_date < :end)";

    private static final String QUERY = """
            SELECT i.code AS item_code, i.name AS item_name, i.kind, u.code AS unit,
                   COALESCE(SUM(m.qty)   FILTER (WHERE %1$s), 0) AS opening_qty,
                   COALESCE(SUM(m.value) FILTER (WHERE %1$s), 0) AS opening_value,
                   COALESCE(SUM(m.qty)   FILTER (WHERE %2$s AND m.movement_type IN ('RECEIPT', 'PRODUCTION_OUTPUT', 'RETURN')), 0) AS receipts_qty,
                   COALESCE(SUM(m.value) FILTER (WHERE %2$s AND m.movement_type IN ('RECEIPT', 'PRODUCTION_OUTPUT', 'RETURN')), 0) AS receipts_value,
                   -COALESCE(SUM(m.qty)   FILTER (WHERE %2$s AND m.movement_type IN ('ISSUE', 'SALE')), 0) AS issues_qty,
                   -COALESCE(SUM(m.value) FILTER (WHERE %2$s AND m.movement_type IN ('ISSUE', 'SALE')), 0) AS issues_value,
                   COALESCE(SUM(m.qty)   FILTER (WHERE %2$s AND m.movement_type IN ('ADJUSTMENT', 'REVERSAL')), 0) AS adjustments_qty,
                   COALESCE(SUM(m.value) FILTER (WHERE %2$s AND m.movement_type IN ('ADJUSTMENT', 'REVERSAL')), 0) AS adjustments_value,
                   COALESCE(SUM(m.qty)   FILTER (WHERE %3$s), 0) AS closing_qty,
                   COALESCE(SUM(m.value) FILTER (WHERE %3$s), 0) AS closing_value
            FROM item i
            JOIN uom u ON u.id = i.base_uom_id
            LEFT JOIN stock_movement m ON m.item_id = i.id
            GROUP BY i.id, i.code, i.name, i.kind, u.code
            ORDER BY i.code""".formatted(OPENING, IN_PERIOD, CLOSING);

    /** One item's movement in the period. Issues are positive here (they are subtracted). */
    public record ItemStock(String itemCode, String itemName, String kind, String unit,
                            BigDecimal openingQty, BigDecimal openingValue, BigDecimal receiptsQty,
                            BigDecimal receiptsValue, BigDecimal issuesQty, BigDecimal issuesValue,
                            BigDecimal adjustmentsQty, BigDecimal adjustmentsValue,
                            BigDecimal closingQty, BigDecimal closingValue) {

        /** R6.4, for quantity and for value. */
        boolean balances() {
            return openingQty.add(receiptsQty).subtract(issuesQty).add(adjustmentsQty).compareTo(closingQty) == 0
                    && openingValue.add(receiptsValue).subtract(issuesValue).add(adjustmentsValue)
                    .compareTo(closingValue) == 0;
        }

        BigDecimal averageCost() {
            return closingQty.signum() > 0
                    ? closingValue.divide(closingQty, Money.RATE_SCALE, Money.ROUNDING)
                    : null;
        }
    }

    private final JdbcClient jdbc;
    private final ReportAssembler assembler;

    public StockSummaryReport(JdbcClient jdbc, ReportAssembler assembler) {
        this.jdbc = jdbc;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<ItemStock> items = items(period);
        List<ReportTable> sections = new ArrayList<>();
        sections.add(section("Raw materials", ItemKind.RAW, items));
        sections.add(section("Packaging", ItemKind.PACKAGING, items));
        sections.add(section("Finished goods", ItemKind.FG, items));
        return assembler.assemble(CODE, "Stock summary", period, user, sections);
    }

    /** Every item, checked against R6.4. A failure means the ledger or this query is wrong: never show it. */
    List<ItemStock> items(ReportPeriod period) {
        List<ItemStock> items = jdbc.sql(QUERY)
                .param("start", ReportPeriod.sqlTimestamp(period.start()))
                .param("end", ReportPeriod.sqlTimestamp(period.endExclusive()))
                .query(ItemStock.class).list();
        for (ItemStock item : items) {
            if (!item.balances()) {
                throw new IllegalStateException("Stock summary does not balance for " + item.itemCode() + ": " + item);
            }
        }
        return items;
    }

    private ReportTable section(String title, ItemKind kind, List<ItemStock> items) {
        ReportTable.Builder table = ReportTable.builder(title)
                .column("itemCode", "Item code", ColumnType.TEXT)
                .column("item", "Item", ColumnType.TEXT)
                .column("unit", "Unit", ColumnType.TEXT)
                .column("opening", "Opening", ColumnType.QTY)
                .column("receipts", "Receipts", ColumnType.QTY)
                .column("issues", "Issues / sales", ColumnType.QTY)
                .column("adjustments", "Adjustments", ColumnType.QTY)
                .column("closing", "Closing", ColumnType.QTY)
                .column("closingValue", "Closing value", ColumnType.MONEY)
                .column("averageCost", "Avg cost", ColumnType.RATE);
        BigDecimal totalValue = TaxAmounts.zero();
        for (ItemStock item : items) {
            if (!item.kind().equals(kind.name())) {
                continue;
            }
            table.row(item.itemCode(), item.itemName(), item.unit(), item.openingQty(), item.receiptsQty(),
                    item.issuesQty(), item.adjustmentsQty(), item.closingQty(), Money.money(item.closingValue()),
                    item.averageCost());
            totalValue = totalValue.add(item.closingValue());
        }
        return table.totals("Total", null, null, null, null, null, null, null, Money.money(totalValue), null).build();
    }
}
