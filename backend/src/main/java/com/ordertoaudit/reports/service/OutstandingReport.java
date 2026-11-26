package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/** R12.4, R15: receivables as of the period's last day, per customer with ageing, then per invoice. */
@Service
public class OutstandingReport {

    public static final String CODE = "outstanding";

    private final OutstandingCalculator calculator;
    private final ReportAssembler assembler;

    public OutstandingReport(OutstandingCalculator calculator, ReportAssembler assembler) {
        this.calculator = calculator;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<OutstandingCalculator.InvoiceDue> dues = calculator.asOf(period.to());
        return assembler.assemble(CODE, "Outstanding with ageing", null, period, period.asOfLabel(), user,
                List.of(byCustomer(dues), byInvoice(dues)));
    }

    private ReportTable byCustomer(List<OutstandingCalculator.InvoiceDue> dues) {
        final class CustomerDue {
            String name;
            long invoices;
            BigDecimal total = TaxAmounts.zero();
            final Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        }
        Map<Long, CustomerDue> byCustomer = new LinkedHashMap<>();
        for (OutstandingCalculator.InvoiceDue due : dues) {
            CustomerDue customer = byCustomer.computeIfAbsent(due.customerId(), id -> new CustomerDue());
            customer.name = due.customerName();
            customer.invoices++;
            customer.total = customer.total.add(due.outstanding());
            customer.buckets.merge(due.bucket(), due.outstanding(), BigDecimal::add);
        }

        ReportTable.Builder table = ReportTable.builder("By customer")
                .column("customer", "Customer", ColumnType.TEXT)
                .column("invoices", "Invoices", ColumnType.COUNT)
                .column("outstanding", "Outstanding", ColumnType.MONEY);
        for (String bucket : OutstandingCalculator.BUCKETS) {
            table.column("days" + bucket, bucket + " days", ColumnType.MONEY);
        }
        long invoices = 0;
        BigDecimal total = TaxAmounts.zero();
        Map<String, BigDecimal> bucketTotals = new LinkedHashMap<>();
        for (CustomerDue customer : byCustomer.values()) {
            Object[] row = new Object[3 + OutstandingCalculator.BUCKETS.size()];
            row[0] = customer.name;
            row[1] = customer.invoices;
            row[2] = customer.total;
            for (int b = 0; b < OutstandingCalculator.BUCKETS.size(); b++) {
                String bucket = OutstandingCalculator.BUCKETS.get(b);
                BigDecimal amount = customer.buckets.getOrDefault(bucket, TaxAmounts.zero());
                row[3 + b] = amount;
                bucketTotals.merge(bucket, amount, BigDecimal::add);
            }
            table.row(row);
            invoices += customer.invoices;
            total = total.add(customer.total);
        }
        Object[] totals = new Object[3 + OutstandingCalculator.BUCKETS.size()];
        totals[0] = "Total";
        totals[1] = invoices;
        totals[2] = total;
        for (int b = 0; b < OutstandingCalculator.BUCKETS.size(); b++) {
            totals[3 + b] = bucketTotals.getOrDefault(OutstandingCalculator.BUCKETS.get(b), TaxAmounts.zero());
        }
        return table.totals(totals).build();
    }

    private ReportTable byInvoice(List<OutstandingCalculator.InvoiceDue> dues) {
        ReportTable.Builder table = ReportTable.builder("By invoice")
                .column("invoiceNo", "Invoice no", ColumnType.TEXT)
                .column("invoiceDate", "Invoice date", ColumnType.DATE)
                .column("customer", "Customer", ColumnType.TEXT)
                .column("total", "Invoice total", ColumnType.MONEY)
                .column("paid", "Paid", ColumnType.MONEY)
                .column("outstanding", "Outstanding", ColumnType.MONEY)
                .column("days", "Days", ColumnType.COUNT)
                .column("bucket", "Ageing", ColumnType.TEXT);
        BigDecimal total = TaxAmounts.zero();
        for (OutstandingCalculator.InvoiceDue due : dues) {
            table.row(due.invoiceNo(), due.invoiceDate(), due.customerName(), due.total(), due.paid(),
                    due.outstanding(), due.days(), due.bucket());
            total = total.add(due.outstanding());
        }
        return table.totals("Total", null, null, null, null, total, null, null).build();
    }
}
