package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.GstStates;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * Outward GST of a period, in the shape of the returns (R15.4):
 *  - GSTR-1 B2B: one row per invoice and rate, to customers with a GSTIN (cancelled ones too: their credit
 *    note is reported separately, as the return requires);
 *  - GSTR-1 B2CS: unregistered customers by place of supply and rate, net of their credit notes;
 *  - GSTR-1 CDNR: credit notes to registered customers, positive amounts, note type C (F5);
 *  - GSTR-1 HSN summary: by B2B / B2C, HSN and rate, net of credit notes;
 *  - GSTR-3B 3.1(a): outward taxable supplies, net of credit notes.
 * Not covered (R15.4): B2CL (large inter-state B2C invoices) and CDNUR.
 */
@Service
public class GstSummaryReport {

    public static final String CODE = "gst-summary";

    private final SalesFacts salesFacts;
    private final ReportAssembler assembler;

    public GstSummaryReport(SalesFacts salesFacts, ReportAssembler assembler) {
        this.salesFacts = salesFacts;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<SalesFacts.Document> documents = salesFacts.documents(period);
        List<SalesFacts.Line> lines = salesFacts.lines(period);
        Map<String, BigDecimal> documentTotals = new LinkedHashMap<>();
        for (SalesFacts.Document d : documents) {
            documentTotals.put(d.documentNo(), d.total());
        }
        return assembler.assemble(CODE, "GST summary (outward supplies)", period, user, List.of(
                b2b(lines, documentTotals), b2cs(lines), cdnr(lines, documentTotals), hsnSummary(lines),
                gstr3b(lines)));
    }

    /** One document at one GST rate: the unit of B2B and CDNR rows. */
    private record DocumentRate(String documentNo, BigDecimal rate) {
    }

    private static BigDecimal rateKey(BigDecimal rate) {
        return rate.setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    private ReportTable b2b(List<SalesFacts.Line> lines, Map<String, BigDecimal> documentTotals) {
        Map<DocumentRate, TaxAmounts> amounts = new LinkedHashMap<>();
        Map<DocumentRate, SalesFacts.Line> firstLine = new LinkedHashMap<>();
        for (SalesFacts.Line line : lines) {
            if (line.isRegistered() && !line.isCreditNote()) {
                DocumentRate key = new DocumentRate(line.documentNo(), rateKey(line.gstRate()));
                amounts.computeIfAbsent(key, k -> new TaxAmounts()).add(line);
                firstLine.putIfAbsent(key, line);
            }
        }
        ReportTable.Builder table = ReportTable.builder("GSTR-1 B2B")
                .column("gstin", "Receiver GSTIN", ColumnType.TEXT)
                .column("receiver", "Receiver", ColumnType.TEXT)
                .column("invoiceNo", "Invoice no", ColumnType.TEXT)
                .column("invoiceDate", "Invoice date", ColumnType.DATE)
                .column("invoiceValue", "Invoice value", ColumnType.MONEY)
                .column("placeOfSupply", "Place of supply", ColumnType.TEXT)
                .column("rate", "Rate %", ColumnType.PERCENT)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY);
        TaxAmounts totals = new TaxAmounts();
        for (Map.Entry<DocumentRate, TaxAmounts> entry : amounts.entrySet()) {
            SalesFacts.Line line = firstLine.get(entry.getKey());
            TaxAmounts a = entry.getValue();
            table.row(line.buyerGstin(), line.buyerName(), line.documentNo(), line.documentDate(),
                    documentTotals.get(line.documentNo()), GstStates.describe(line.placeOfSupply()),
                    entry.getKey().rate(), a.taxable, a.igst, a.cgst, a.sgst);
            totals.add(a);
        }
        return table.totals("Total", null, null, null, null, null, null,
                totals.taxable, totals.igst, totals.cgst, totals.sgst).build();
    }

    private ReportTable b2cs(List<SalesFacts.Line> lines) {
        record PlaceRate(String placeOfSupply, String supplyType, BigDecimal rate) {
        }
        Map<PlaceRate, TaxAmounts> amounts = new LinkedHashMap<>();
        for (SalesFacts.Line line : lines) {
            if (!line.isRegistered()) {
                PlaceRate key = new PlaceRate(line.placeOfSupply(), line.supplyType(), rateKey(line.gstRate()));
                amounts.computeIfAbsent(key, k -> new TaxAmounts()).add(line);
            }
        }
        ReportTable.Builder table = ReportTable.builder("GSTR-1 B2CS")
                .column("placeOfSupply", "Place of supply", ColumnType.TEXT)
                .column("supplyType", "Supply type", ColumnType.TEXT)
                .column("rate", "Rate %", ColumnType.PERCENT)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY);
        TaxAmounts totals = new TaxAmounts();
        for (Map.Entry<PlaceRate, TaxAmounts> entry : amounts.entrySet()) {
            PlaceRate key = entry.getKey();
            TaxAmounts a = entry.getValue();
            table.row(GstStates.describe(key.placeOfSupply()), supplyTypeLabel(key.supplyType()), key.rate(),
                    a.taxable, a.igst, a.cgst, a.sgst);
            totals.add(a);
        }
        return table.totals("Total", null, null, totals.taxable, totals.igst, totals.cgst, totals.sgst).build();
    }

    /** Positive amounts, as on the note itself. GSTR-3B below subtracts them. */
    private ReportTable cdnr(List<SalesFacts.Line> lines, Map<String, BigDecimal> documentTotals) {
        Map<DocumentRate, TaxAmounts> amounts = new LinkedHashMap<>();
        Map<DocumentRate, SalesFacts.Line> firstLine = new LinkedHashMap<>();
        for (SalesFacts.Line line : lines) {
            if (line.isRegistered() && line.isCreditNote()) {
                DocumentRate key = new DocumentRate(line.documentNo(), rateKey(line.gstRate()));
                amounts.computeIfAbsent(key, k -> new TaxAmounts())
                        .add(line.taxableValue(), line.cgst(), line.sgst(), line.igst());
                firstLine.putIfAbsent(key, line);
            }
        }
        ReportTable.Builder table = ReportTable.builder("GSTR-1 CDNR")
                .column("gstin", "Receiver GSTIN", ColumnType.TEXT)
                .column("receiver", "Receiver", ColumnType.TEXT)
                .column("noteNo", "Note no", ColumnType.TEXT)
                .column("noteDate", "Note date", ColumnType.DATE)
                .column("noteType", "Note type", ColumnType.TEXT)
                .column("invoiceNo", "Against invoice", ColumnType.TEXT)
                .column("placeOfSupply", "Place of supply", ColumnType.TEXT)
                .column("noteValue", "Note value", ColumnType.MONEY)
                .column("rate", "Rate %", ColumnType.PERCENT)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY);
        TaxAmounts totals = new TaxAmounts();
        for (Map.Entry<DocumentRate, TaxAmounts> entry : amounts.entrySet()) {
            SalesFacts.Line line = firstLine.get(entry.getKey());
            TaxAmounts a = entry.getValue();
            table.row(line.buyerGstin(), line.buyerName(), line.documentNo(), line.documentDate(), "C",
                    line.invoiceNo(), GstStates.describe(line.placeOfSupply()), documentTotals.get(line.documentNo()),
                    entry.getKey().rate(), a.taxable, a.igst, a.cgst, a.sgst);
            totals.add(a);
        }
        return table.totals("Total", null, null, null, null, null, null, null, null,
                totals.taxable, totals.igst, totals.cgst, totals.sgst).build();
    }

    private ReportTable hsnSummary(List<SalesFacts.Line> lines) {
        record HsnKey(String type, String hsnCode, String uqc, BigDecimal rate) {
        }
        final class HsnTotals {
            String description;
            BigDecimal qty = BigDecimal.ZERO;
            final TaxAmounts amounts = new TaxAmounts();
        }
        Map<HsnKey, HsnTotals> byHsn = new LinkedHashMap<>();
        for (SalesFacts.Line line : lines) {
            HsnKey key = new HsnKey(line.isRegistered() ? "B2B" : "B2C", line.hsnCode(),
                    GstUnits.uqc(line.unitCode()), rateKey(line.gstRate()));
            HsnTotals totals = byHsn.computeIfAbsent(key, k -> new HsnTotals());
            totals.description = line.itemName();
            totals.qty = totals.qty.add(line.signed(line.qty()));
            totals.amounts.add(line);
        }
        ReportTable.Builder table = ReportTable.builder("GSTR-1 HSN summary")
                .column("type", "B2B / B2C", ColumnType.TEXT)
                .column("hsn", "HSN", ColumnType.TEXT)
                .column("description", "Description", ColumnType.TEXT)
                .column("uqc", "UQC", ColumnType.TEXT)
                .column("rate", "Rate %", ColumnType.PERCENT)
                .column("qty", "Total qty", ColumnType.QTY)
                .column("totalValue", "Total value", ColumnType.MONEY)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY);
        TaxAmounts totals = new TaxAmounts();
        for (Map.Entry<HsnKey, HsnTotals> entry : byHsn.entrySet()) {
            HsnKey key = entry.getKey();
            TaxAmounts a = entry.getValue().amounts;
            table.row(key.type(), key.hsnCode(), entry.getValue().description, key.uqc(), key.rate(),
                    entry.getValue().qty, a.total(), a.taxable, a.igst, a.cgst, a.sgst);
            totals.add(a);
        }
        return table.totals("Total", null, null, null, null, null, totals.total(),
                totals.taxable, totals.igst, totals.cgst, totals.sgst).build();
    }

    private ReportTable gstr3b(List<SalesFacts.Line> lines) {
        TaxAmounts net = new TaxAmounts();
        lines.forEach(net::add);
        return ReportTable.builder("GSTR-3B 3.1(a)")
                .column("nature", "Nature of supplies", ColumnType.TEXT)
                .column("taxable", "Taxable", ColumnType.MONEY)
                .column("igst", "IGST", ColumnType.MONEY)
                .column("cgst", "CGST", ColumnType.MONEY)
                .column("sgst", "SGST", ColumnType.MONEY)
                .column("cess", "Cess", ColumnType.MONEY)
                .row("(a) Outward taxable supplies (other than zero rated, nil rated and exempted)",
                        net.taxable, net.igst, net.cgst, net.sgst, TaxAmounts.zero())
                .build();
    }

    private static String supplyTypeLabel(String supplyType) {
        return "INTRA".equals(supplyType) ? "Intra-state" : "Inter-state";
    }
}
