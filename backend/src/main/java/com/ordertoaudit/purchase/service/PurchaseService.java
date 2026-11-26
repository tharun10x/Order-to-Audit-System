package com.ordertoaudit.purchase.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.service.InventoryService;
import com.ordertoaudit.inventory.service.MovementRequest;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.entity.Supplier;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.repository.SupplierRepository;
import com.ordertoaudit.masters.service.UnitConversionService;
import com.ordertoaudit.purchase.entity.Purchase;
import com.ordertoaudit.purchase.entity.PurchaseLine;
import com.ordertoaudit.purchase.entity.PurchaseStatus;
import com.ordertoaudit.purchase.repository.PurchaseRepository;
import com.ordertoaudit.purchase.web.PurchaseRequest;
import com.ordertoaudit.purchase.web.PurchaseResponse;

/** Records supplier bills (R7.1, R7.2). */
@Service
public class PurchaseService {

    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final PurchaseRepository purchaseRepository;
    private final UnitConversionService unitConversionService;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public PurchaseService(SupplierRepository supplierRepository, ItemRepository itemRepository,
                           PurchaseRepository purchaseRepository, UnitConversionService unitConversionService,
                           InventoryService inventoryService, AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.itemRepository = itemRepository;
        this.purchaseRepository = purchaseRepository;
        this.unitConversionService = unitConversionService;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    /**
     * One transaction (all or nothing): validate, save the bill and its lines, post one RECEIPT per line,
     * write the audit row. If anything fails, nothing is saved.
     */
    @Transactional
    public PurchaseResponse record(PurchaseRequest request, AppUser user) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new BadRequestException("Unknown supplier id " + request.supplierId()));
        if (!supplier.isActive()) {
            throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive");
        }
        if (purchaseRepository.existsBySupplierIdAndBillNo(supplier.getId(), request.billNo())) {
            throw new ConflictException("Bill " + request.billNo() + " from this supplier is already recorded");
        }

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setBillNo(request.billNo());
        purchase.setStatus(PurchaseStatus.POSTED);
        purchase.setCreatedBy(user);

        Set<String> itemCodesSeen = new HashSet<>();
        for (PurchaseRequest.Line line : request.lines()) {
            purchase.addLine(buildLine(line, itemCodesSeen));
        }
        purchaseRepository.save(purchase);

        // One RECEIPT per line. The value is the bill amount, so stock value = sum of bill amounts.
        Instant movementDate = request.purchaseDate().atStartOfDay(Money.ZONE).toInstant();
        List<MovementRequest> receipts = new ArrayList<>();
        for (PurchaseLine line : purchase.getLines()) {
            receipts.add(MovementRequest.inbound(line.getItem().getId(), MovementType.RECEIPT, line.getQty(),
                    line.getAmount(), SourceType.PURCHASE, purchase.getId(), movementDate, user));
        }
        inventoryService.post(receipts);

        PurchaseResponse response = toResponse(purchase);
        auditService.log("PURCHASE", purchase.getId(), AuditAction.POST, null, auditData(response), user);
        return response;
    }

    /** All recorded bills, newest bill date first. */
    @Transactional(readOnly = true)
    public List<PurchaseResponse> list() {
        return purchaseRepository.findAllByOrderByPurchaseDateDescIdDesc().stream().map(this::toResponse).toList();
    }

    private PurchaseLine buildLine(PurchaseRequest.Line line, Set<String> itemCodesSeen) {
        Item item = itemRepository.findByCode(line.itemCode())
                .orElseThrow(() -> new BadRequestException("Unknown item '" + line.itemCode() + "'"));
        if (!item.isActive()) {
            throw new BadRequestException("Item " + item.getCode() + " is inactive");
        }
        if (item.getKind() == ItemKind.FG) {
            throw new BadRequestException("Item " + item.getCode() + " is a finished good and cannot be purchased");
        }
        if (!itemCodesSeen.add(item.getCode())) {
            throw new BadRequestException("Item " + item.getCode() + " appears on more than one line of the bill");
        }

        BigDecimal baseQty = unitConversionService.toBase(item.getId(), line.qty(), line.uomCode());
        if (baseQty.signum() <= 0) {
            throw new BadRequestException("Quantity for " + item.getCode() + " is too small to record");
        }

        // The bill says: entered qty x entered rate = amount. That amount is the truth.
        BigDecimal exactAmount = line.qty().multiply(line.rate());
        // Rate per base unit follows from it (60000 g at 0.045 per g = 45 per kg)
        BigDecimal ratePerBase = exactAmount.divide(baseQty, Money.RATE_SCALE, Money.ROUNDING);

        PurchaseLine purchaseLine = new PurchaseLine();
        purchaseLine.setItem(item);
        purchaseLine.setQty(baseQty);
        purchaseLine.setRate(ratePerBase);
        purchaseLine.setAmount(Money.money(exactAmount));
        return purchaseLine;
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        List<PurchaseResponse.Line> lines = purchase.getLines().stream()
                .sorted(Comparator.comparing(l -> l.getItem().getCode()))
                .map(l -> new PurchaseResponse.Line(l.getItem().getCode(), l.getQty(), l.getRate(), l.getAmount()))
                .toList();
        BigDecimal total = lines.stream().map(PurchaseResponse.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseResponse(purchase.getId(), purchase.getSupplier().getId(), purchase.getSupplier().getName(),
                purchase.getPurchaseDate(), purchase.getBillNo(), purchase.getStatus(), total, lines);
    }

    /** What goes into audit_log.after_data: plain values only, so it becomes clean JSON. */
    private Map<String, Object> auditData(PurchaseResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("billNo", response.billNo());
        data.put("supplierId", response.supplierId());
        data.put("purchaseDate", response.purchaseDate().toString());
        data.put("status", response.status().name());
        data.put("total", response.total());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PurchaseResponse.Line line : response.lines()) {
            Map<String, Object> lineData = new LinkedHashMap<>();
            lineData.put("itemCode", line.itemCode());
            lineData.put("qty", line.qty());
            lineData.put("rate", line.rate());
            lineData.put("amount", line.amount());
            lines.add(lineData);
        }
        data.put("lines", lines);
        return data;
    }
}
