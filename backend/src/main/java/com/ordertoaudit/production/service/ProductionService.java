package com.ordertoaudit.production.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.numbering.DocType;
import com.ordertoaudit.common.numbering.DocumentNumberService;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.entity.StockMovement;
import com.ordertoaudit.inventory.service.InventoryService;
import com.ordertoaudit.inventory.service.MovementRequest;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Bom;
import com.ordertoaudit.masters.entity.BomLine;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.entity.UomDimension;
import com.ordertoaudit.masters.repository.BomRepository;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.service.UnitConversionService;
import com.ordertoaudit.production.entity.OverheadEntry;
import com.ordertoaudit.production.entity.ProductionConsumption;
import com.ordertoaudit.production.entity.ProductionRun;
import com.ordertoaudit.production.entity.RunStatus;
import com.ordertoaudit.production.repository.OverheadEntryRepository;
import com.ordertoaudit.production.repository.ProductionRunRepository;
import com.ordertoaudit.production.web.ActualsRequest;
import com.ordertoaudit.production.web.CreateRunRequest;
import com.ordertoaudit.production.web.OverheadRequest;
import com.ordertoaudit.production.web.RunResponse;

/**
 * Production runs: DRAFT -> POSTED, or DRAFT -> CANCELLED (R8). Every method is one transaction and writes
 * an audit row. Every method that changes a run locks the run row first, then (inside InventoryService)
 * the item rows in ascending id order, so two requests can never post the same run twice.
 */
@Service
public class ProductionService {

    private final ProductionRunRepository runRepository;
    private final OverheadEntryRepository overheadRepository;
    private final ItemRepository itemRepository;
    private final BomRepository bomRepository;
    private final UnitConversionService unitConversionService;
    private final InventoryService inventoryService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;
    private final RunReportService reportService;

    public ProductionService(ProductionRunRepository runRepository, OverheadEntryRepository overheadRepository,
                             ItemRepository itemRepository, BomRepository bomRepository,
                             UnitConversionService unitConversionService, InventoryService inventoryService,
                             DocumentNumberService documentNumberService, AuditService auditService,
                             RunReportService reportService) {
        this.runRepository = runRepository;
        this.overheadRepository = overheadRepository;
        this.itemRepository = itemRepository;
        this.bomRepository = bomRepository;
        this.unitConversionService = unitConversionService;
        this.inventoryService = inventoryService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
        this.reportService = reportService;
    }

    /** R8.1, R8.4, R8.5: a DRAFT run with the standards copied from the product's ACTIVE BOM. No stock effect. */
    @Transactional
    public RunResponse create(CreateRunRequest request, AppUser user) {
        Item product = itemRepository.findByCode(request.productCode())
                .orElseThrow(() -> new BadRequestException("Unknown product '" + request.productCode() + "'"));
        if (product.getKind() != ItemKind.FG || !product.isActive()) {
            throw new BadRequestException("'" + product.getCode() + "' is not an active finished good");
        }
        Bom bom = bomRepository.findByProductIdAndActiveTrue(product.getId())
                .orElseThrow(() -> new BadRequestException("Product " + product.getCode() + " has no active BOM"));

        BigDecimal batches = Money.qty(request.batches());

        ProductionRun run = new ProductionRun();
        run.setRunNo(documentNumberService.next(DocType.PRODUCTION_RUN, request.runDate()));
        run.setBom(bom);
        run.setRunDate(request.runDate());
        run.setBatches(batches);
        run.setPlannedOutput(Money.qty(bom.getOutputQty().multiply(batches)));
        run.setStatus(RunStatus.DRAFT);
        run.setCreatedBy(user);

        for (BomLine bomLine : bom.getLines()) {
            ProductionConsumption line = new ProductionConsumption();
            line.setItem(bomLine.getItem());
            // INGREDIENT: bom qty x batches. PACKAGING: the same number for now, recalculated per good packet at posting (R8.6)
            line.setPlannedQty(Money.qty(bomLine.getQty().multiply(batches)));
            line.setScrapQty(Money.qty(BigDecimal.ZERO));
            run.addConsumption(line);
        }
        runRepository.save(run);

        auditService.log("PRODUCTION_RUN", run.getId(), AuditAction.CREATE, null, snapshot(run), user);
        return reportService.build(run);
    }

    /** R8.4: what was really issued (scrap included), how much of it was scrap, and the good output. DRAFT only. */
    @Transactional
    public RunResponse enterActuals(Long runId, ActualsRequest request, AppUser user) {
        ProductionRun run = lockRun(runId);
        requireDraft(run, "have actuals entered");
        Map<String, Object> before = snapshot(run);

        Map<String, ProductionConsumption> linesByItemCode = new LinkedHashMap<>();
        for (ProductionConsumption line : run.getConsumptions()) {
            linesByItemCode.put(line.getItem().getCode(), line);
        }

        Set<String> seen = new HashSet<>();
        for (ActualsRequest.Line entered : request.lines()) {
            ProductionConsumption line = linesByItemCode.get(entered.itemCode());
            if (line == null) {
                throw new BadRequestException("Item '" + entered.itemCode() + "' is not part of the recipe of run " + run.getRunNo());
            }
            if (!seen.add(entered.itemCode())) {
                throw new BadRequestException("Item " + entered.itemCode() + " appears twice");
            }
            Long itemId = line.getItem().getId();
            BigDecimal actual = unitConversionService.toBase(itemId, entered.actualQty(), entered.uomCode());
            BigDecimal scrap = (entered.scrapQty() == null)
                    ? Money.qty(BigDecimal.ZERO)
                    : unitConversionService.toBase(itemId, entered.scrapQty(), entered.uomCode());
            if (scrap.compareTo(actual) > 0) {
                throw new BadRequestException("Scrap of " + entered.itemCode() + " (" + scrap.toPlainString()
                        + ") is more than the quantity issued (" + actual.toPlainString() + ")");
            }
            line.setActualQty(actual);
            line.setScrapQty(scrap);
        }
        run.setActualOutput(Money.qty(request.actualOutput()));

        auditService.log("PRODUCTION_RUN", run.getId(), AuditAction.UPDATE, before, snapshot(run), user);
        return reportService.build(run);
    }

    /**
     * R8.2: the only moment a run moves stock, and it can happen once. One transaction, one InventoryService call:
     * an ISSUE per material, then the PRODUCTION_OUTPUT valued at the total of those issues (R13.6).
     * If any material is short, nothing is written and the run stays DRAFT.
     */
    @Transactional
    public RunResponse post(Long runId, AppUser user) {
        ProductionRun run = lockRun(runId);
        requireDraft(run, "posted");
        requireActualsEntered(run);
        Map<String, Object> before = snapshot(run);

        recalculatePackagingStandards(run);

        Instant movementDate = run.getRunDate().atStartOfDay(Money.ZONE).toInstant();
        List<MovementRequest> requests = new ArrayList<>();
        List<ProductionConsumption> issuedLines = new ArrayList<>();
        for (ProductionConsumption line : run.getConsumptions()) {
            if (line.getActualQty().signum() > 0) {
                issuedLines.add(line);
                requests.add(MovementRequest.outbound(line.getItem().getId(), MovementType.ISSUE, line.getActualQty(),
                        SourceType.PRODUCTION_RUN, run.getId(), movementDate, user));
            }
        }
        Item product = run.getBom().getProduct();
        requests.add(MovementRequest.inboundAtCostOfEarlierOutbounds(product.getId(), MovementType.PRODUCTION_OUTPUT,
                run.getActualOutput(), SourceType.PRODUCTION_RUN, run.getId(), movementDate, user));

        List<StockMovement> posted = inventoryService.post(requests);

        // Each line remembers the average cost its ISSUE was valued at. The movements come back in request order.
        for (int i = 0; i < issuedLines.size(); i++) {
            issuedLines.get(i).setUnitCost(posted.get(i).getUnitCost());
        }
        run.setStatus(RunStatus.POSTED);
        run.setPostedAt(Instant.now());
        run.setPostedBy(user);

        auditService.log("PRODUCTION_RUN", run.getId(), AuditAction.POST, before, snapshot(run), user);
        return reportService.build(run);
    }

    /** R8.1: a DRAFT run can be cancelled. It never touched stock, so there is nothing to undo. */
    @Transactional
    public RunResponse cancel(Long runId, AppUser user) {
        ProductionRun run = lockRun(runId);
        requireDraft(run, "cancelled");
        Map<String, Object> before = snapshot(run);

        run.setStatus(RunStatus.CANCELLED);

        auditService.log("PRODUCTION_RUN", run.getId(), AuditAction.CANCEL, before, snapshot(run), user);
        return reportService.build(run);
    }

    /** R13.3: labour, power or other cost for this run. Allowed before and after posting (benchmark step 8 adds it after). */
    @Transactional
    public RunResponse addOverhead(Long runId, OverheadRequest request, AppUser user) {
        ProductionRun run = lockRun(runId);
        if (run.getStatus() != RunStatus.DRAFT && run.getStatus() != RunStatus.POSTED) {
            throw new ConflictException("Run " + run.getRunNo() + " is " + run.getStatus() + ": overheads can only be added to DRAFT or POSTED runs");
        }

        OverheadEntry entry = new OverheadEntry();
        entry.setRun(run);
        entry.setType(request.type());
        entry.setAmount(Money.money(request.amount()));
        entry.setNote(request.note());
        entry.setCreatedBy(user);
        overheadRepository.save(entry);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("runNo", run.getRunNo());
        after.put("type", entry.getType().name());
        after.put("amount", entry.getAmount());
        after.put("note", entry.getNote());
        auditService.log("OVERHEAD_ENTRY", entry.getId(), AuditAction.CREATE, null, after, user);
        return reportService.build(run);
    }

    private ProductionRun lockRun(Long runId) {
        return runRepository.lockById(runId)
                .orElseThrow(() -> new NotFoundException("Production run " + runId + " not found"));
    }

    private void requireDraft(ProductionRun run, String action) {
        if (run.getStatus() != RunStatus.DRAFT) {
            throw new ConflictException("Run " + run.getRunNo() + " is " + run.getStatus()
                    + " and cannot be " + action + " (only DRAFT runs can)");
        }
    }

    /** The run needs its good output (more than 0) and an actual quantity on every line. 0 is a valid line quantity. */
    private void requireActualsEntered(ProductionRun run) {
        if (run.getActualOutput() == null || run.getActualOutput().signum() <= 0) {
            throw new BadRequestException("Enter the good output (more than 0) before posting run " + run.getRunNo());
        }
        List<String> missing = run.getConsumptions().stream()
                .filter(line -> line.getActualQty() == null)
                .map(line -> line.getItem().getCode())
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Enter the actual quantity for: " + String.join(", ", missing));
        }
    }

    /**
     * R8.6: packaging is judged against what was actually packed, not against the planned output.
     *   standard = qty per batch / expected yield x good output
     * For counted items (pouches, cartons) only whole units count: 188 packets need 9 whole cartons, not 9.4.
     * The run's own copy is used (planned qty = bom qty x batches, planned output = yield x batches),
     * so later BOM changes cannot change it (R8.5).
     */
    private void recalculatePackagingStandards(ProductionRun run) {
        for (ProductionConsumption line : run.getConsumptions()) {
            Item item = line.getItem();
            if (item.getKind() != ItemKind.PACKAGING) {
                continue;
            }
            BigDecimal perBatchTimesGood = line.getPlannedQty().multiply(run.getActualOutput());
            BigDecimal standard;
            if (item.getBaseUom().getDimension() == UomDimension.COUNT) {
                standard = perBatchTimesGood.divideToIntegralValue(run.getPlannedOutput());   // whole units, rounded down exactly
            } else {
                standard = perBatchTimesGood.divide(run.getPlannedOutput(), Money.QTY_SCALE, Money.ROUNDING);
            }
            line.setPlannedQty(Money.qty(standard));
        }
    }

    /** A small JSON-friendly picture of the run for the audit log. */
    private Map<String, Object> snapshot(ProductionRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runNo", run.getRunNo());
        data.put("status", run.getStatus().name());
        data.put("batches", run.getBatches());
        data.put("plannedOutput", run.getPlannedOutput());
        data.put("actualOutput", run.getActualOutput());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ProductionConsumption line : run.getConsumptions()) {
            Map<String, Object> lineData = new LinkedHashMap<>();
            lineData.put("itemCode", line.getItem().getCode());
            lineData.put("plannedQty", line.getPlannedQty());
            lineData.put("actualQty", line.getActualQty());
            lineData.put("scrapQty", line.getScrapQty());
            lines.add(lineData);
        }
        data.put("lines", lines);
        return data;
    }
}
