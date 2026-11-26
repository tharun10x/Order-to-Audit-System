package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.repository.CustomerRepository;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * Brief 4.1, R2.2: a salesman's customer-wise sales, for the customers assigned to them only (F12). Every
 * assigned customer is listed, also one without activity. Orders = orders dated in the period, any status.
 * Invoiced = invoices less credit notes of the period, as in the sales register. Outstanding = as of "to".
 */
@Service
public class MySalesReport {

    public static final String CODE = "my-sales";

    private static final String ORDER_COUNTS = """
            SELECT o.customer_id, count(*) AS orders
            FROM sales_order o
            JOIN customer c ON c.id = o.customer_id
            WHERE c.salesman_id = :salesmanId AND o.order_date BETWEEN :from AND :to
            GROUP BY o.customer_id""";

    private record OrderCount(Long customerId, Long orders) {
    }

    private final JdbcClient jdbc;
    private final CustomerRepository customerRepository;
    private final SalesFacts salesFacts;
    private final OutstandingCalculator outstandingCalculator;
    private final ReportAssembler assembler;

    public MySalesReport(JdbcClient jdbc, CustomerRepository customerRepository, SalesFacts salesFacts,
                         OutstandingCalculator outstandingCalculator, ReportAssembler assembler) {
        this.jdbc = jdbc;
        this.customerRepository = customerRepository;
        this.salesFacts = salesFacts;
        this.outstandingCalculator = outstandingCalculator;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser salesman) {
        List<Customer> customers = customerRepository.findBySalesmanIdAndActiveTrueOrderByNameAsc(salesman.getId());

        Map<Long, Long> orders = new HashMap<>();
        jdbc.sql(ORDER_COUNTS).param("salesmanId", salesman.getId())
                .param("from", period.from()).param("to", period.to())
                .query(OrderCount.class).list()
                .forEach(count -> orders.put(count.customerId(), count.orders()));
        Map<Long, BigDecimal> invoiced = new HashMap<>();
        for (SalesFacts.Document d : salesFacts.documents(period)) {
            invoiced.merge(d.customerId(), d.signed(d.total()), BigDecimal::add);
        }
        Map<Long, BigDecimal> outstanding = new HashMap<>();
        for (OutstandingCalculator.InvoiceDue due : outstandingCalculator.asOf(period.to())) {
            outstanding.merge(due.customerId(), due.outstanding(), BigDecimal::add);
        }

        ReportTable.Builder table = ReportTable.builder("Customer-wise sales")
                .column("customerCode", "Code", ColumnType.TEXT)
                .column("customer", "Customer", ColumnType.TEXT)
                .column("orders", "Orders", ColumnType.COUNT)
                .column("invoiced", "Invoiced", ColumnType.MONEY)
                .column("outstanding", "Outstanding", ColumnType.MONEY);
        long totalOrders = 0;
        BigDecimal totalInvoiced = TaxAmounts.zero();
        BigDecimal totalOutstanding = TaxAmounts.zero();
        for (Customer customer : customers) {
            long orderCount = orders.getOrDefault(customer.getId(), 0L);
            BigDecimal invoicedValue = invoiced.getOrDefault(customer.getId(), TaxAmounts.zero());
            BigDecimal due = outstanding.getOrDefault(customer.getId(), TaxAmounts.zero());
            table.row(customer.getCode(), customer.getName(), orderCount, invoicedValue, due);
            totalOrders += orderCount;
            totalInvoiced = totalInvoiced.add(invoicedValue);
            totalOutstanding = totalOutstanding.add(due);
        }
        table.totals("Total", null, totalOrders, totalInvoiced, totalOutstanding);
        return assembler.assemble(CODE, "My sales: " + salesman.getFullName(), period, salesman,
                List.of(table.build()));
    }
}
