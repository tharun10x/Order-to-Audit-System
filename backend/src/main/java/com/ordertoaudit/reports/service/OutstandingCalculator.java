package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;

/**
 * What customers owed at the end of a given day (F3, R12.3, R12.4). The invoice_outstanding view only knows
 * today, so for a past date this works it out again:
 *  - invoices dated on or before that day,
 *  - minus those cancelled by a credit note dated on or before that day,
 *  - less the allocations of payments received on or before that day that were not yet reversed by its end.
 * For today it gives the same figures as the view.
 */
@Component
public class OutstandingCalculator {

    /** One invoice with money still due. days = days from the invoice date to the "as of" day. */
    public record InvoiceDue(Long invoiceId, String invoiceNo, LocalDate invoiceDate, Long customerId,
                             String customerName, BigDecimal total, BigDecimal paid, BigDecimal outstanding,
                             long days) {

        /** R12.4 ageing buckets. */
        public String bucket() {
            return bucketOf(days);
        }
    }

    public static final List<String> BUCKETS = List.of("0-30", "31-60", "61-90", "90+");

    private static final String QUERY = """
            SELECT i.id AS invoice_id, i.invoice_no, i.invoice_date, i.customer_id, c.name AS customer_name, i.total,
                   COALESCE((SELECT SUM(a.amount)
                             FROM payment_allocation a
                             JOIN payment p ON p.id = a.payment_id
                             WHERE a.invoice_id = i.id
                               AND p.received_on <= :asOf
                               AND (p.status = 'POSTED' OR p.reversed_at >= :endOfDay)), 0) AS paid
            FROM invoice i
            JOIN customer c ON c.id = i.customer_id
            WHERE i.invoice_date <= :asOf
              AND NOT EXISTS (SELECT 1 FROM credit_note cn WHERE cn.invoice_id = i.id AND cn.note_date <= :asOf)
            ORDER BY c.name, i.invoice_date, i.invoice_no""";

    private record Row(Long invoiceId, String invoiceNo, LocalDate invoiceDate, Long customerId, String customerName,
                       BigDecimal total, BigDecimal paid) {
    }

    private final JdbcClient jdbc;

    public OutstandingCalculator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Only invoices with something still to pay. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<InvoiceDue> asOf(LocalDate asOf) {
        Object endOfDay = ReportPeriod.sqlTimestamp(asOf.plusDays(1).atStartOfDay(Money.ZONE).toInstant());
        return jdbc.sql(QUERY).param("asOf", asOf).param("endOfDay", endOfDay)
                .query(Row.class).list().stream()
                .map(row -> new InvoiceDue(row.invoiceId(), row.invoiceNo(), row.invoiceDate(), row.customerId(),
                        row.customerName(), row.total(), Money.money(row.paid()),
                        Money.money(row.total().subtract(row.paid())),
                        ChronoUnit.DAYS.between(row.invoiceDate(), asOf)))
                .filter(due -> due.outstanding().signum() > 0)
                .toList();
    }

    static String bucketOf(long days) {
        if (days <= 30) {
            return BUCKETS.get(0);
        }
        if (days <= 60) {
            return BUCKETS.get(1);
        }
        return days <= 90 ? BUCKETS.get(2) : BUCKETS.get(3);
    }
}
