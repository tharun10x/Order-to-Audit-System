package com.ordertoaudit.production.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.repository.ItemValue;
import com.ordertoaudit.inventory.repository.StockMovementRepository;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.repository.CompanyRepository;
import com.ordertoaudit.production.entity.OverheadEntry;
import com.ordertoaudit.production.entity.OverheadType;
import com.ordertoaudit.production.entity.ProductionConsumption;
import com.ordertoaudit.production.entity.ProductionRun;
import com.ordertoaudit.production.entity.RunStatus;
import com.ordertoaudit.production.repository.OverheadEntryRepository;
import com.ordertoaudit.production.repository.ProductionRunRepository;
import com.ordertoaudit.production.web.RunResponse;
import com.ordertoaudit.production.web.RunSummaryResponse;

/**
 * Variance and cost report of a run (R8.7 to R8.9, R13). Everything is calculated on request from the run's
 * own copied standards and the stock ledger. Nothing is stored, so the report is never stale.
 */
@Service
public class RunReportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductionRunRepository runRepository;
    private final OverheadEntryRepository overheadRepository;
    private final StockMovementRepository movementRepository;
    private final CompanyRepository companyRepository;

    public RunReportService(ProductionRunRepository runRepository, OverheadEntryRepository overheadRepository,
                            StockMovementRepository movementRepository, CompanyRepository companyRepository) {
        this.runRepository = runRepository;
        this.overheadRepository = overheadRepository;
        this.movementRepository = movementRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public RunResponse report(Long runId) {
        ProductionRun run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Production run " + runId + " not found"));
        return build(run);
    }

    @Transactional(readOnly = true)
    public List<RunSummaryResponse> list(LocalDate from, LocalDate to) {
        LocalDate start = (from == null) ? LocalDate.of(1900, 1, 1) : from;
        LocalDate end = (to == null) ? LocalDate.of(2999, 12, 31) : to;
        // The same report as the run screen, so the list and the run can never disagree
        return runRepository.findByRunDateBetweenOrderByRunDateAscIdAsc(start, end).stream()
                .map(this::build)
                .map(r -> new RunSummaryResponse(r.id(), r.runNo(), r.productCode(), r.runDate(), r.status(),
                        r.batches(), r.plannedOutput(), r.actualOutput(), r.yieldVariancePct(), r.yieldFlagged(),
                        r.costing() == null ? null : r.costing().costPerPacketRounded()))
                .toList();
    }

    /** Builds the report. Called inside the caller's transaction, so it also works right after a post. */
    public RunResponse build(ProductionRun run) {
        BigDecimal tolerance = companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No company row"))
                .getVarianceTolerancePct();
        Item product = run.getBom().getProduct();

        // What each item's ISSUE actually cost, from the ledger (stored negative, shown positive)
        Map<Long, BigDecimal> issueValueByItem = new HashMap<>();
        for (ItemValue v : movementRepository.sumValueByItem(SourceType.PRODUCTION_RUN, run.getId(), MovementType.ISSUE)) {
            issueValueByItem.put(v.getItemId(), v.getTotal().negate());
        }

        List<RunResponse.Line> lines = new ArrayList<>();
        for (ProductionConsumption c : run.getConsumptions()) {
            lines.add(toLine(c, issueValueByItem.get(c.getItem().getId()), tolerance));
        }

        BigDecimal yieldVariance = variance(run.getPlannedOutput(), run.getActualOutput());
        boolean yieldFlagged = isFlagged(run.getPlannedOutput(), run.getActualOutput(), yieldVariance, tolerance);

        List<OverheadEntry> overheadEntries = overheadRepository.findByRunIdOrderById(run.getId());
        List<RunResponse.Overhead> overheads = overheadEntries.stream()
                .map(o -> new RunResponse.Overhead(o.getId(), o.getType(), o.getAmount(), o.getNote()))
                .toList();

        RunResponse.Costing costing = (run.getStatus() == RunStatus.POSTED)
                ? buildCosting(run, product, lines, overheadEntries)
                : null;

        return new RunResponse(run.getId(), run.getRunNo(), product.getCode(), product.getName(), run.getRunDate(),
                run.getStatus(), run.getBatches(), run.getPlannedOutput(), run.getActualOutput(),
                yieldVariance, yieldFlagged, tolerance, run.getPostedAt(),
                run.getPostedBy() == null ? null : run.getPostedBy().getUsername(),
                lines, overheads, costing);
    }

    private RunResponse.Line toLine(ProductionConsumption c, BigDecimal value, BigDecimal tolerance) {
        BigDecimal variance = variance(c.getPlannedQty(), c.getActualQty());
        boolean flagged = isFlagged(c.getPlannedQty(), c.getActualQty(), variance, tolerance);
        Item item = c.getItem();
        return new RunResponse.Line(item.getCode(), item.getName(), item.getKind(), item.getBaseUom().getCode(),
                c.getPlannedQty(),
                c.getActualQty(), c.getScrapQty(), c.getUnitCost(), value, variance, flagged);
    }

    /** R13.1 to R13.5. Cost per packet and margin keep 4 decimals and are rounded only for display (R4.4). */
    private RunResponse.Costing buildCosting(ProductionRun run, Item product, List<RunResponse.Line> lines,
                                             List<OverheadEntry> overheadEntries) {
        BigDecimal material = BigDecimal.ZERO;
        BigDecimal packaging = BigDecimal.ZERO;
        for (RunResponse.Line line : lines) {
            BigDecimal value = (line.value() == null) ? BigDecimal.ZERO : line.value();
            if (line.kind() == ItemKind.PACKAGING) {
                packaging = packaging.add(value);
            } else {
                material = material.add(value);
            }
        }

        Map<OverheadType, BigDecimal> overheadsByType = new EnumMap<>(OverheadType.class);
        for (OverheadType type : OverheadType.values()) {
            overheadsByType.put(type, Money.money(BigDecimal.ZERO));
        }
        BigDecimal overheadTotal = Money.money(BigDecimal.ZERO);
        for (OverheadEntry entry : overheadEntries) {
            overheadsByType.merge(entry.getType(), entry.getAmount(), BigDecimal::add);
            overheadTotal = overheadTotal.add(entry.getAmount());
        }

        BigDecimal totalCost = material.add(packaging).add(overheadTotal);
        BigDecimal costPerPacket = totalCost.divide(run.getActualOutput(), Money.RATE_SCALE, Money.ROUNDING);

        BigDecimal price = product.getSellingPrice();
        BigDecimal margin = price.subtract(costPerPacket);
        BigDecimal marginPct = (price.signum() == 0) ? null
                : margin.multiply(HUNDRED).divide(price, 2, Money.ROUNDING);

        return new RunResponse.Costing(material, packaging, overheadsByType, overheadTotal, totalCost,
                costPerPacket, Money.money(costPerPacket), price, margin, Money.money(margin), marginPct);
    }

    /** (actual - planned) / planned x 100 at 2 decimals. Null when it cannot be calculated. */
    private BigDecimal variance(BigDecimal planned, BigDecimal actual) {
        if (planned == null || actual == null || planned.signum() == 0) {
            return null;
        }
        return actual.subtract(planned).multiply(HUNDRED).divide(planned, 2, Money.ROUNDING);
    }

    /**
     * R8.9: flagged when |variance| is above the tolerance. The 2-decimal figure that is shown is the one compared.
     * When nothing was planned there is no percentage, so any actual use is flagged.
     */
    private boolean isFlagged(BigDecimal planned, BigDecimal actual, BigDecimal variance, BigDecimal tolerance) {
        if (actual == null || planned == null) {
            return false;
        }
        if (planned.signum() == 0) {
            return actual.signum() > 0;
        }
        return variance.abs().compareTo(tolerance) > 0;
    }
}
