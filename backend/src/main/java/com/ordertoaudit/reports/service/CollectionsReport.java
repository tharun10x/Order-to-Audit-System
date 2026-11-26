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

/** R15: the POSTED payments received in the period and the invoices each one settled. Reversed ones are left out. */
@Service
public class CollectionsReport {

    public static final String CODE = "collections";

    private static final String QUERY = """
            SELECT p.received_on, c.name AS customer, p.mode, p.reference, p.amount,
                   string_agg(i.invoice_no || ': ' || to_char(a.amount, 'FM999999999990.00'), ', '
                              ORDER BY i.invoice_no) AS allocated_to
            FROM payment p
            JOIN customer c ON c.id = p.customer_id
            JOIN payment_allocation a ON a.payment_id = p.id
            JOIN invoice i ON i.id = a.invoice_id
            WHERE p.status = 'POSTED' AND p.received_on BETWEEN :from AND :to
            GROUP BY p.id, p.received_on, c.name, p.mode, p.reference, p.amount
            ORDER BY p.received_on, p.id""";

    private record Collection(LocalDate receivedOn, String customer, String mode, String reference,
                              BigDecimal amount, String allocatedTo) {
    }

    private final JdbcClient jdbc;
    private final ReportAssembler assembler;

    public CollectionsReport(JdbcClient jdbc, ReportAssembler assembler) {
        this.jdbc = jdbc;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<Collection> payments = jdbc.sql(QUERY).param("from", period.from()).param("to", period.to())
                .query(Collection.class).list();
        ReportTable.Builder table = ReportTable.builder("Payments received")
                .column("date", "Date", ColumnType.DATE)
                .column("customer", "Customer", ColumnType.TEXT)
                .column("mode", "Mode", ColumnType.TEXT)
                .column("reference", "Reference", ColumnType.TEXT)
                .column("amount", "Amount", ColumnType.MONEY)
                .column("allocatedTo", "Allocated to", ColumnType.TEXT);
        BigDecimal total = TaxAmounts.zero();
        for (Collection payment : payments) {
            table.row(payment.receivedOn(), payment.customer(), payment.mode(), payment.reference(), payment.amount(),
                    payment.allocatedTo());
            total = total.add(payment.amount());
        }
        table.totals(null, "Total", null, null, total, null);
        return assembler.assemble(CODE, "Payments and collections", period, user, List.of(table.build()));
    }
}
