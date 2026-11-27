package com.ordertoaudit.masters.service;

import java.math.BigDecimal;
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
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Bom;
import com.ordertoaudit.masters.entity.BomLine;
import com.ordertoaudit.masters.entity.BomLineKind;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.repository.BomRepository;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.web.BomRequest;
import com.ordertoaudit.masters.web.BomResponse;

import jakarta.persistence.EntityManager;

/**
 * Recipes (BOMs), R5.6 to R5.10. A version is never edited: a change is a new version (M12), saved inactive and
 * then activated, which deactivates the previous one in the same transaction. Runs copied their standard
 * quantities when they were created, so activating a version never changes an existing run (R8.5).
 */
@Service
public class BomService {

    private static final String ENTITY = "BOM";

    private final BomRepository bomRepository;
    private final ItemRepository itemRepository;
    private final UnitConversionService unitConversionService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public BomService(BomRepository bomRepository, ItemRepository itemRepository,
                      UnitConversionService unitConversionService, AuditService auditService,
                      EntityManager entityManager) {
        this.bomRepository = bomRepository;
        this.itemRepository = itemRepository;
        this.unitConversionService = unitConversionService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    /** Every version of a product's recipe, newest first; at most one is active. */
    @Transactional(readOnly = true)
    public List<BomResponse> versions(String productCode) {
        Item product = product(productCode);
        return bomRepository.findByProductIdOrderByVersionDesc(product.getId()).stream().map(BomResponse::from).toList();
    }

    /** M11, M12: a new inactive version, numbered after the last one. */
    @Transactional
    public BomResponse create(BomRequest request, AppUser user) {
        Item product = product(request.productCode());
        if (!product.isActive()) {
            throw new BadRequestException(product.getCode() + " is inactive: reactivate it before giving it a recipe");
        }
        BigDecimal outputQty = request.outputQty();
        if (outputQty.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("Packets per batch must be a whole number");
        }

        Bom bom = new Bom();
        bom.setProduct(product);
        bom.setVersion(bomRepository.lastVersion(product.getId()) + 1);
        bom.setOutputQty(Money.qty(outputQty));
        bom.setActive(false);

        Set<Long> itemIds = new HashSet<>();
        if (MasterRules.text(request.copyIngredientsFrom()) != null) {
            copyIngredients(bom, request.copyIngredientsFrom().trim(), itemIds);
        }
        for (BomRequest.Line requested : request.lines()) {
            Item item = itemRepository.findByCode(requested.itemCode())
                    .orElseThrow(() -> new BadRequestException("Unknown item '" + requested.itemCode() + "'"));
            String unit = MasterRules.text(requested.uomCode()) == null
                    ? item.getBaseUom().getCode() : requested.uomCode().trim();
            BigDecimal qty = unitConversionService.toBase(item.getId(), requested.qty(), unit);    // R5.9
            if (qty.signum() <= 0) {
                throw new BadRequestException(item.getCode() + ": " + requested.qty() + " " + unit
                        + " is less than the smallest quantity stored (0.0001 " + item.getBaseUom().getCode() + ")");
            }
            addLine(bom, item, qty, itemIds);
        }
        if (bom.getLines().stream().noneMatch(line -> line.getLineKind() == BomLineKind.INGREDIENT)) {
            throw new BadRequestException("A recipe needs at least one raw-material (ingredient) line");
        }

        bomRepository.saveAndFlush(bom);
        entityManager.refresh(bom);                     // created_at is set by the database
        auditService.log(ENTITY, bom.getId(), AuditAction.CREATE, null, snapshot(bom), user);
        return BomResponse.from(bom);
    }

    /**
     * M12: makes this version the active one. The product row is locked first, so two activations of the same
     * product run one after the other; the old version is switched off and flushed before the new one is
     * switched on, because the database allows one active version per product (ux_bom_one_active).
     */
    @Transactional
    public BomResponse activate(Long bomId, AppUser user) {
        Bom bom = bomRepository.findById(bomId).orElseThrow(() -> new NotFoundException("Recipe " + bomId + " not found"));
        itemRepository.lockAllByIdOrderById(List.of(bom.getProduct().getId()));
        entityManager.refresh(bom);
        if (bom.isActive()) {
            throw new ConflictException("Version " + bom.getVersion() + " is already the active recipe of "
                    + bom.getProduct().getCode());
        }
        for (BomLine line : bom.getLines()) {
            if (!line.getItem().isActive()) {
                throw new ConflictException(line.getItem().getCode() + " is inactive. Reactivate it, or create a "
                        + "version without it");
            }
        }
        bomRepository.findByProductIdAndActiveTrue(bom.getProduct().getId()).ifPresent(previous -> {
            Map<String, Object> before = snapshot(previous);
            previous.setActive(false);
            bomRepository.saveAndFlush(previous);
            auditService.log(ENTITY, previous.getId(), AuditAction.UPDATE, before, snapshot(previous), user);
        });
        Map<String, Object> before = snapshot(bom);
        bom.setActive(true);
        bomRepository.saveAndFlush(bom);
        auditService.log(ENTITY, bom.getId(), AuditAction.UPDATE, before, snapshot(bom), user);
        return BomResponse.from(bom);
    }

    private Item product(String productCode) {
        Item product = itemRepository.findByCode(productCode)
                .orElseThrow(() -> new NotFoundException("Product " + productCode + " not found"));
        if (product.getKind() != ItemKind.FG) {
            throw new BadRequestException(product.getCode() + " is not a finished good, so it has no recipe");
        }
        return product;
    }

    /**
     * R5.10: the same ingredients at the same quantities per batch. The new version is independent: a later change
     * to the source recipe does not change it.
     */
    private void copyIngredients(Bom bom, String sourceCode, Set<Long> itemIds) {
        Item source = product(sourceCode);
        Bom sourceBom = bomRepository.findByProductIdAndActiveTrue(source.getId())
                .orElseThrow(() -> new BadRequestException(source.getCode() + " has no active recipe to copy from"));
        for (BomLine line : sourceBom.getLines()) {
            if (line.getLineKind() == BomLineKind.INGREDIENT) {
                addLine(bom, line.getItem(), line.getQty(), itemIds);
            }
        }
    }

    /** M11: the line kind follows the item's kind, so the two cannot disagree. No finished goods, no repeats. */
    private void addLine(Bom bom, Item item, BigDecimal qty, Set<Long> itemIds) {
        BomLineKind kind = switch (item.getKind()) {
            case RAW -> BomLineKind.INGREDIENT;
            case PACKAGING -> BomLineKind.PACKAGING;
            case FG -> throw new BadRequestException(item.getCode()
                    + " is a finished good: a recipe uses raw materials and packaging only");
        };
        if (!item.isActive()) {
            throw new BadRequestException(item.getCode() + " is inactive");
        }
        if (!itemIds.add(item.getId())) {
            throw new BadRequestException(item.getCode() + " appears twice in the recipe (copied lines included)");
        }
        BomLine line = new BomLine();
        line.setBom(bom);
        line.setItem(item);
        line.setQty(qty);
        line.setLineKind(kind);
        bom.getLines().add(line);
    }

    private static Map<String, Object> snapshot(Bom bom) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (BomLine line : bom.getLines()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("item", line.getItem().getCode());
            values.put("kind", line.getLineKind().name());
            values.put("qty", line.getQty());
            lines.add(values);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("product", bom.getProduct().getCode());
        values.put("version", bom.getVersion());
        values.put("outputQty", bom.getOutputQty());
        values.put("active", bom.isActive());
        values.put("lines", lines);
        return values;
    }
}
