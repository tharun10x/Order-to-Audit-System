package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.inventory.service.SourceDocuments;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * R6.2, R15: every movement of one item in the period with a running balance and the source document it came
 * from. It starts from the same opening balance as the stock summary (F8), so the two always agree.
 */
@Service
public class StockLedgerReport {

    public static final String CODE = "stock-ledger";

    private static final String OPENING = """
            SELECT COALESCE(SUM(m.qty), 0) AS qty, COALESCE(SUM(m.value), 0) AS value
            FROM stock_movement m
            WHERE m.item_id = :itemId AND %s""".formatted(StockSummaryReport.OPENING);

    /** The document text comes from SourceDocuments, the same as the stock ledger screen. */
    private static final String MOVEMENTS = """
            SELECT m.id, m.movement_date, m.movement_type, m.qty, m.unit_cost, m.value
            FROM stock_movement m
            WHERE m.item_id = :itemId AND m.movement_type <> 'OPENING'
              AND m.movement_date >= :start AND m.movement_date < :end
            ORDER BY m.movement_date, m.id""";

    private record Balance(BigDecimal qty, BigDecimal value) {
    }

    private record Movement(Long id, OffsetDateTime movementDate, String movementType, BigDecimal qty,
                            BigDecimal unitCost, BigDecimal value) {
    }

    private final JdbcClient jdbc;
    private final ItemRepository itemRepository;
    private final SourceDocuments sourceDocuments;
    private final ReportAssembler assembler;

    public StockLedgerReport(JdbcClient jdbc, ItemRepository itemRepository, SourceDocuments sourceDocuments,
                             ReportAssembler assembler) {
        this.jdbc = jdbc;
        this.itemRepository = itemRepository;
        this.sourceDocuments = sourceDocuments;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(String itemCode, ReportPeriod period, AppUser user) {
        Item item = itemRepository.findByCode(itemCode)
                .orElseThrow(() -> new BadRequestException("Unknown item code '" + itemCode + "'"));
        Object start = ReportPeriod.sqlTimestamp(period.start());
        Object end = ReportPeriod.sqlTimestamp(period.endExclusive());

        Balance opening = jdbc.sql(OPENING).param("itemId", item.getId()).param("start", start)
                .query(Balance.class).single();
        List<Movement> movements = jdbc.sql(MOVEMENTS).param("itemId", item.getId()).param("start", start)
                .param("end", end).query(Movement.class).list();
        Map<Long, SourceDocuments.SourceDocument> documents =
                sourceDocuments.forMovements(movements.stream().map(Movement::id).toList());

        ReportTable.Builder table = ReportTable.builder("Movements of " + item.getCode())
                .column("date", "Date", ColumnType.DATE)
                .column("movement", "Movement", ColumnType.TEXT)
                .column("document", "Source document", ColumnType.TEXT)
                .column("qtyIn", "Qty in", ColumnType.QTY)
                .column("qtyOut", "Qty out", ColumnType.QTY)
                .column("unitCost", "Unit cost", ColumnType.RATE)
                .column("value", "Value", ColumnType.MONEY)
                .column("balanceQty", "Balance qty", ColumnType.QTY)
                .column("balanceValue", "Balance value", ColumnType.MONEY);

        BigDecimal qty = opening.qty();
        BigDecimal value = Money.money(opening.value());
        table.row(period.from(), "Opening balance", null, null, null, null, null, qty, value);

        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Movement m : movements) {
            qty = qty.add(m.qty());
            value = value.add(m.value());
            BigDecimal in = m.qty().signum() > 0 ? m.qty() : null;
            BigDecimal out = m.qty().signum() < 0 ? m.qty().negate() : null;
            totalIn = (in == null) ? totalIn : totalIn.add(in);
            totalOut = (out == null) ? totalOut : totalOut.add(out);
            LocalDate date = m.movementDate().atZoneSameInstant(Money.ZONE).toLocalDate();
            table.row(date, m.movementType(), documents.get(m.id()).documentNo(), in, out, m.unitCost(), m.value(), qty, value);
        }
        table.totals(period.to(), "Closing balance", null, totalIn, totalOut, null, null, qty, value);

        String title = "Stock ledger: " + item.getName() + " (" + item.getCode() + ")";
        return assembler.assemble(CODE, title, item.getCode(), period, period.label(), user, List.of(table.build()));
    }
}
