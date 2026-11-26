package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.billing.service.GstStates;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * R15: every invoice of the period, and every credit note of the period as a negative row (F4), then the
 * same figures by customer and by product.
 */
@Service
public class SalesRegisterReport {

    public static final String CODE = "sales-register";

    private final SalesFacts salesFacts;
    private final ReportAssembler assembler;

    public SalesRegisterReport(SalesFacts salesFacts, ReportAssembler assembler) {
        this.salesFacts = salesFacts;
        this.assembler = assembler;
    }

    /** REPEATABLE READ: all queries of one report see the same snapshot of the data. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<SalesFacts.Document> documents = salesFacts.documents(period);
        List<SalesFacts.Line> lines = salesFacts.lines(period);
        return assembler.assemble(CODE, "Sales register", period, user,
                List.of(register(documents), byCustomer(documents), byProduct(lines)));
    }

    private ReportTable register(List<SalesFacts.Document> documents) {
        ReportTable.Builder table = ReportTable.builder("Invoices and credit notes")
                .column("type", "Type", ColumnType.TEXT)
                .column("documentNo", "Document no", ColumnType.TEXT)
                .column("date", "Date", ColumnType.DATE)
                .column("invoiceNo", "Against invoice", ColumnType.TEXT)
                .column("customer", "Customer", ColumnType.TEXT)
                .column("gstin", "GSTIN", ColumnType.TEXT)
                .column("placeOfSupply", "Place of supply", ColumnType.TEXT)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("total", "Total", ColumnType.MONEY)
                .column("status", "Status", ColumnType.TEXT);
        TaxAmounts totals = new TaxAmounts();
        for (SalesFacts.Document d : documents) {
            BigDecimal taxable = d.signed(d.taxableValue());
            BigDecimal cgst = d.signed(d.cgst());
            BigDecimal sgst = d.signed(d.sgst());
            BigDecimal igst = d.signed(d.igst());
            totals.add(taxable, cgst, sgst, igst);
            table.row(d.isCreditNote() ? "Credit note" : "Invoice", d.documentNo(), d.documentDate(),
                    d.isCreditNote() ? d.invoiceNo() : null, d.buyerName(), d.buyerGstin(),
                    GstStates.describe(d.placeOfSupply()), taxable, cgst, sgst, igst, d.signed(d.total()), d.status());
        }
        return table.totals("Total", null, null, null, null, null, null,
                totals.taxable, totals.cgst, totals.sgst, totals.igst, totals.total(), null).build();
    }

    private ReportTable byCustomer(List<SalesFacts.Document> documents) {
        // Per customer: the name and GSTIN as printed on its invoices
        final class CustomerSales {
            String name;
            String gstin;
            long invoices;
            long creditNotes;
            final TaxAmounts amounts = new TaxAmounts();
        }
        Map<Long, CustomerSales> byCustomer = new LinkedHashMap<>();
        for (SalesFacts.Document d : documents) {
            CustomerSales sales = byCustomer.computeIfAbsent(d.customerId(), id -> new CustomerSales());
            sales.name = d.buyerName();
            sales.gstin = d.buyerGstin();
            if (d.isCreditNote()) {
                sales.creditNotes++;
            } else {
                sales.invoices++;
            }
            sales.amounts.add(d.signed(d.taxableValue()), d.signed(d.cgst()), d.signed(d.sgst()), d.signed(d.igst()));
        }

        ReportTable.Builder table = ReportTable.builder("Customer-wise sales")
                .column("customer", "Customer", ColumnType.TEXT)
                .column("gstin", "GSTIN", ColumnType.TEXT)
                .column("invoices", "Invoices", ColumnType.COUNT)
                .column("creditNotes", "Credit notes", ColumnType.COUNT)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("total", "Total", ColumnType.MONEY);
        TaxAmounts totals = new TaxAmounts();
        long invoices = 0;
        long creditNotes = 0;
        List<CustomerSales> sorted = new ArrayList<>(byCustomer.values());
        sorted.sort(Comparator.comparing(sales -> sales.name));
        for (CustomerSales sales : sorted) {
            TaxAmounts a = sales.amounts;
            table.row(sales.name, sales.gstin, sales.invoices, sales.creditNotes, a.taxable, a.cgst, a.sgst, a.igst,
                    a.total());
            totals.add(a);
            invoices += sales.invoices;
            creditNotes += sales.creditNotes;
        }
        return table.totals("Total", null, invoices, creditNotes, totals.taxable, totals.cgst, totals.sgst,
                totals.igst, totals.total()).build();
    }

    private ReportTable byProduct(List<SalesFacts.Line> lines) {
        record ProductKey(String itemCode, String hsnCode) {
        }
        final class ProductSales {
            String name;
            BigDecimal qty = BigDecimal.ZERO;
            final TaxAmounts amounts = new TaxAmounts();
        }
        Map<ProductKey, ProductSales> byProduct = new LinkedHashMap<>();
        for (SalesFacts.Line line : lines) {
            ProductSales sales = byProduct.computeIfAbsent(new ProductKey(line.itemCode(), line.hsnCode()),
                    key -> new ProductSales());
            sales.name = line.itemName();
            sales.qty = sales.qty.add(line.signed(line.qty()));
            sales.amounts.add(line);
        }

        ReportTable.Builder table = ReportTable.builder("Product-wise sales")
                .column("itemCode", "Product code", ColumnType.TEXT)
                .column("product", "Product", ColumnType.TEXT)
                .column("hsn", "HSN", ColumnType.TEXT)
                .column("qty", "Qty (packets)", ColumnType.QTY)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("total", "Total", ColumnType.MONEY);
        TaxAmounts totals = new TaxAmounts();
        BigDecimal qty = BigDecimal.ZERO;
        for (Map.Entry<ProductKey, ProductSales> entry : byProduct.entrySet()) {
            ProductSales sales = entry.getValue();
            TaxAmounts a = sales.amounts;
            table.row(entry.getKey().itemCode(), sales.name, entry.getKey().hsnCode(), sales.qty,
                    a.taxable, a.cgst, a.sgst, a.igst, a.total());
            totals.add(a);
            qty = qty.add(sales.qty);
        }
        return table.totals("Total", null, null, qty, totals.taxable, totals.cgst, totals.sgst, totals.igst,
                totals.total()).build();
    }
}
