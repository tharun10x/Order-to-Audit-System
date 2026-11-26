package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * R15: every bill line of the period. Quantity and rate are in the item's base unit (R7.1).
 * A reversed purchase is listed with its status but left out of the total.
 */
@Service
public class PurchaseRegisterReport {

    public static final String CODE = "purchase-register";

    private static final String QUERY = """
            SELECT p.purchase_date, p.bill_no, s.name AS supplier, i.code AS item_code, i.name AS item_name,
                   l.qty, u.code AS unit, l.rate, l.amount, p.status
            FROM purchase p
            JOIN supplier s ON s.id = p.supplier_id
            JOIN purchase_line l ON l.purchase_id = p.id
            JOIN item i ON i.id = l.item_id
            JOIN uom u ON u.id = i.base_uom_id
            WHERE p.purchase_date BETWEEN :from AND :to
            ORDER BY p.purchase_date, p.bill_no, i.code""";

    private record BillLine(LocalDate purchaseDate, String billNo, String supplier, String itemCode, String itemName,
                            BigDecimal qty, String unit, BigDecimal rate, BigDecimal amount, String status) {
    }

    private final JdbcClient jdbc;
    private final ReportAssembler assembler;

    public PurchaseRegisterReport(JdbcClient jdbc, ReportAssembler assembler) {
        this.jdbc = jdbc;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<BillLine> lines = jdbc.sql(QUERY).param("from", period.from()).param("to", period.to())
                .query(BillLine.class).list();

        ReportTable.Builder table = ReportTable.builder("Purchase bills")
                .column("date", "Date", ColumnType.DATE)
                .column("billNo", "Bill no", ColumnType.TEXT)
                .column("supplier", "Supplier", ColumnType.TEXT)
                .column("itemCode", "Item code", ColumnType.TEXT)
                .column("item", "Item", ColumnType.TEXT)
                .column("qty", "Qty", ColumnType.QTY)
                .column("unit", "Unit", ColumnType.TEXT)
                .column("rate", "Rate", ColumnType.RATE)
                .column("amount", "Amount", ColumnType.MONEY)
                .column("status", "Status", ColumnType.TEXT);
        BigDecimal total = TaxAmounts.zero();
        for (BillLine line : lines) {
            table.row(line.purchaseDate(), line.billNo(), line.supplier(), line.itemCode(), line.itemName(),
                    line.qty(), line.unit(), line.rate(), line.amount(), line.status());
            if (!"REVERSED".equals(line.status())) {
                total = total.add(line.amount());
            }
        }
        table.totals(null, "Total (excluding reversed)", null, null, null, null, null, null, total, null);
        return assembler.assemble(CODE, "Purchase register", period, user, List.of(table.build()));
    }
}
