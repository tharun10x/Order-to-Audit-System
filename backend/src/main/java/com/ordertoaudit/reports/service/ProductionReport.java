package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.production.entity.ProductionRun;
import com.ordertoaudit.production.entity.RunStatus;
import com.ordertoaudit.production.repository.ProductionRunRepository;
import com.ordertoaudit.production.service.RunReportService;
import com.ordertoaudit.production.web.RunResponse;
import com.ordertoaudit.reports.model.ColumnType;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/**
 * R15: the POSTED runs of the period, then one consumption table per run. Variances, flags and costs come
 * from RunReportService, the same calculation as the run screen (R8.7 to R8.9, R13), so they cannot disagree.
 */
@Service
public class ProductionReport {

    public static final String CODE = "production-report";
    /** Marks a variance beyond the tolerance (R8.9). DejaVu Sans has this glyph, so the PDF shows it. */
    public static final String FLAG = "⚑";

    private final ProductionRunRepository runRepository;
    private final RunReportService runReportService;
    private final ReportAssembler assembler;

    public ProductionReport(ProductionRunRepository runRepository, RunReportService runReportService,
                            ReportAssembler assembler) {
        this.runRepository = runRepository;
        this.runReportService = runReportService;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report build(ReportPeriod period, AppUser user) {
        List<RunResponse> runs = new ArrayList<>();
        for (ProductionRun run : runRepository.findByStatusAndRunDateBetweenOrderByRunDateAscIdAsc(
                RunStatus.POSTED, period.from(), period.to())) {
            runs.add(runReportService.build(run));
        }
        List<ReportTable> sections = new ArrayList<>();
        sections.add(summary(runs));
        for (RunResponse run : runs) {
            sections.add(consumption(run));
        }
        return assembler.assemble(CODE, "Production report", period, user, sections);
    }

    private ReportTable summary(List<RunResponse> runs) {
        ReportTable.Builder table = ReportTable.builder("Runs")
                .column("runNo", "Run no", ColumnType.TEXT)
                .column("date", "Date", ColumnType.DATE)
                .column("product", "Product", ColumnType.TEXT)
                .column("batches", "Batches", ColumnType.QTY)
                .column("expected", "Expected output", ColumnType.QTY)
                .column("good", "Good output", ColumnType.QTY)
                .column("yieldVariance", "Yield variance %", ColumnType.PERCENT)
                .column("yieldFlag", "Flag", ColumnType.TEXT)
                .column("material", "Material", ColumnType.MONEY)
                .column("packaging", "Packaging", ColumnType.MONEY)
                .column("overheads", "Overheads", ColumnType.MONEY)
                .column("totalCost", "Total cost", ColumnType.MONEY)
                .column("costPerPacket", "Cost per packet", ColumnType.MONEY);
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal good = BigDecimal.ZERO;
        BigDecimal material = TaxAmounts.zero();
        BigDecimal packaging = TaxAmounts.zero();
        BigDecimal overheads = TaxAmounts.zero();
        BigDecimal totalCost = TaxAmounts.zero();
        for (RunResponse run : runs) {
            RunResponse.Costing cost = run.costing();
            table.row(run.runNo(), run.runDate(), run.productName(), run.batches(), run.plannedOutput(),
                    run.actualOutput(), run.yieldVariancePct(), run.yieldFlagged() ? FLAG : null,
                    cost.materialCost(), cost.packagingCost(), cost.overheadTotal(), cost.totalCost(),
                    cost.costPerPacketRounded());
            expected = expected.add(run.plannedOutput());
            good = good.add(run.actualOutput());
            material = material.add(cost.materialCost());
            packaging = packaging.add(cost.packagingCost());
            overheads = overheads.add(cost.overheadTotal());
            totalCost = totalCost.add(cost.totalCost());
        }
        return table.totals("Total", null, null, null, expected, good, null, null, material, packaging, overheads,
                totalCost, null).build();
    }

    /** Standard vs actual per item. actual includes scrap (R8.4); value is what the ISSUE cost (R13.1, R13.2). */
    private ReportTable consumption(RunResponse run) {
        ReportTable.Builder table = ReportTable.builder(run.runNo() + " consumption")
                .column("itemCode", "Item code", ColumnType.TEXT)
                .column("item", "Item", ColumnType.TEXT)
                .column("kind", "Kind", ColumnType.TEXT)
                .column("standard", "Standard", ColumnType.QTY)
                .column("actual", "Actual (incl. scrap)", ColumnType.QTY)
                .column("scrap", "Scrap", ColumnType.QTY)
                .column("variance", "Variance %", ColumnType.PERCENT)
                .column("flag", "Flag", ColumnType.TEXT)
                .column("unitCost", "Unit cost", ColumnType.RATE)
                .column("value", "Value", ColumnType.MONEY);
        BigDecimal total = TaxAmounts.zero();
        for (RunResponse.Line line : run.lines()) {
            table.row(line.itemCode(), line.itemName(), line.kind().name(), line.plannedQty(), line.actualQty(),
                    line.scrapQty(), line.variancePct(), line.flagged() ? FLAG : null, line.unitCost(), line.value());
            if (line.value() != null) {
                total = total.add(line.value());
            }
        }
        return table.totals("Total", null, null, null, null, null, null, null, null, total).build();
    }
}
