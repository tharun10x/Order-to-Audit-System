package com.ordertoaudit.masters.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.entity.ItemUomConversion;
import com.ordertoaudit.masters.entity.Uom;
import com.ordertoaudit.masters.repository.BomRepository;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.repository.ItemUomConversionRepository;
import com.ordertoaudit.masters.repository.UomRepository;
import com.ordertoaudit.masters.web.ItemRequest;
import com.ordertoaudit.masters.web.ItemResponse;

/**
 * The Admin's item master: raw materials, packaging and finished goods (R5.1, R5.2, R5.4). Code and kind never
 * change (M3); the base unit is locked once the item has moved or is in a recipe (M7). A price change only
 * affects new orders, because an order copies the price when it is taken (R9.2). Every change is audited (R14).
 */
@Service
public class ItemAdminService {

    private static final String ENTITY = "ITEM";
    private static final String PACKET = "pkt";
    private static final Pattern HSN = Pattern.compile("[0-9]{4}|[0-9]{6}|[0-9]{8}");

    private final ItemRepository itemRepository;
    private final UomRepository uomRepository;
    private final ItemUomConversionRepository conversionRepository;
    private final BomRepository bomRepository;
    private final ItemService itemService;
    private final AuditService auditService;

    public ItemAdminService(ItemRepository itemRepository, UomRepository uomRepository,
                            ItemUomConversionRepository conversionRepository, BomRepository bomRepository,
                            ItemService itemService, AuditService auditService) {
        this.itemRepository = itemRepository;
        this.uomRepository = uomRepository;
        this.conversionRepository = conversionRepository;
        this.bomRepository = bomRepository;
        this.itemService = itemService;
        this.auditService = auditService;
    }

    @Transactional
    public ItemResponse create(ItemRequest request, AppUser user) {
        String code = MasterRules.code(request.code(), "item");
        if (itemRepository.existsByCode(code)) {
            throw new ConflictException("Item code " + code + " is already used");
        }
        if (request.kind() == null) {
            throw new BadRequestException("Choose the kind: raw material, packaging or finished good");
        }
        Item item = new Item();
        item.setCode(code);
        item.setKind(request.kind());
        item.setActive(true);
        item.setBaseUom(baseUnit(request.baseUnit(), request.kind()));
        apply(item, request);
        itemRepository.save(item);
        syncCarton(item, request.packetsPerCarton());
        auditService.log(ENTITY, item.getId(), AuditAction.CREATE, null, snapshot(item), user);
        return itemService.toResponse(item);
    }

    @Transactional
    public ItemResponse update(String code, ItemRequest request, AppUser user) {
        Item item = find(code);
        if (request.code() != null && !MasterRules.code(request.code(), "item").equals(item.getCode())) {
            throw new BadRequestException("An item's code cannot be changed (it is " + item.getCode() + ")");
        }
        if (request.kind() != null && request.kind() != item.getKind()) {
            throw new BadRequestException("An item's kind cannot be changed: create a new item instead");
        }
        Map<String, Object> before = snapshot(item);
        Uom newBase = baseUnit(request.baseUnit(), item.getKind());
        if (!newBase.getId().equals(item.getBaseUom().getId())) {
            String lockReason = itemService.baseUnitLockReason(item);
            if (lockReason != null) {
                throw new ConflictException(lockReason);
            }
            item.setBaseUom(newBase);
        }
        apply(item, request);
        syncCarton(item, request.packetsPerCarton());
        auditService.log(ENTITY, item.getId(), AuditAction.UPDATE, before, snapshot(item), user);
        return itemService.toResponse(item);
    }

    /**
     * R5.4: deactivate or reactivate. M10: an item an ACTIVE recipe uses stays active, or posting a run of that
     * recipe would consume an inactive item. A finished good can be deactivated: no new orders or runs for it.
     */
    @Transactional
    public ItemResponse setActive(String code, boolean active, AppUser user) {
        Item item = find(code);
        if (item.isActive() != active) {
            if (!active) {
                List<String> products = bomRepository.productsWhoseActiveBomUses(item.getId());
                if (!products.isEmpty()) {
                    throw new ConflictException(item.getCode() + " is in the active recipe of "
                            + String.join(", ", products) + ". Activate a recipe version without it first");
                }
            }
            Map<String, Object> before = snapshot(item);
            item.setActive(active);
            auditService.log(ENTITY, item.getId(), AuditAction.UPDATE, before, snapshot(item), user);
        }
        return itemService.toResponse(item);
    }

    private Item find(String code) {
        return itemRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Item " + code + " not found"));
    }

    /** M8: a base unit has factor 1 (kg, L, pc, pkt); a finished good is counted in packets. */
    private Uom baseUnit(String unitCode, ItemKind kind) {
        String code = MasterRules.text(unitCode);
        if (code == null) {
            throw new BadRequestException("Choose the base unit");
        }
        Uom uom = uomRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Unknown unit '" + code + "'"));
        if (uom.getFactorToBase() == null || uom.getFactorToBase().compareTo(BigDecimal.ONE) != 0) {
            throw new BadRequestException("The base unit must be kg, L, pc or pkt. Smaller units such as g and ml "
                    + "convert into these, and cartons are set as packets per carton");
        }
        if (kind == ItemKind.FG && !PACKET.equals(uom.getCode())) {
            throw new BadRequestException("A finished good is counted in packets: its base unit is pkt");
        }
        return uom;
    }

    /** The fields every edit may change, and the finished-goods fields (R5.1, M9). */
    private void apply(Item item, ItemRequest request) {
        item.setName(MasterRules.text(request.name()));
        item.setReorderLevel(request.reorderLevel());
        if (item.getKind() == ItemKind.FG) {
            String hsn = MasterRules.text(request.hsnCode());
            if (hsn == null || !HSN.matcher(hsn).matches()) {
                throw new BadRequestException("A finished good needs an HSN code of 4, 6 or 8 digits");
            }
            item.setHsnCode(hsn);
            item.setGstRate(required(request.gstRate(), "GST rate"));
            item.setSellingPrice(required(request.sellingPrice(), "selling price"));
            item.setPackWeightKg(required(request.packWeightKg(), "pack weight"));
        } else {
            if (MasterRules.text(request.hsnCode()) != null || request.gstRate() != null
                    || request.sellingPrice() != null || request.packWeightKg() != null
                    || request.packetsPerCarton() != null) {
                throw new BadRequestException("Only finished goods have an HSN code, GST rate, selling price, "
                        + "pack weight and packets per carton");
            }
            item.setHsnCode(null);
            item.setGstRate(null);
            item.setSellingPrice(null);
            item.setPackWeightKg(null);
        }
    }

    private static BigDecimal required(BigDecimal value, String what) {
        if (value == null) {
            throw new BadRequestException("A finished good needs its " + what);
        }
        return value;
    }

    /** R5.1: packets per carton is the item's ctn conversion. null removes it. */
    private void syncCarton(Item item, Integer packetsPerCarton) {
        Optional<ItemUomConversion> existing = conversionRepository.findByItemIdAndUomCode(item.getId(), ItemService.CARTON);
        if (packetsPerCarton == null) {
            existing.ifPresent(conversionRepository::delete);
            return;
        }
        ItemUomConversion carton = existing.orElseGet(() -> {
            ItemUomConversion conversion = new ItemUomConversion();
            conversion.setItem(item);
            conversion.setUom(uomRepository.findByCode(ItemService.CARTON)
                    .orElseThrow(() -> new IllegalStateException("The ctn unit is missing")));
            return conversion;
        });
        carton.setQtyInBase(BigDecimal.valueOf(packetsPerCarton));
        conversionRepository.save(carton);
    }

    private Map<String, Object> snapshot(Item item) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", item.getCode());
        values.put("name", item.getName());
        values.put("kind", item.getKind().name());
        values.put("baseUnit", item.getBaseUom().getCode());
        values.put("reorderLevel", item.getReorderLevel());
        values.put("hsnCode", item.getHsnCode());
        values.put("gstRate", item.getGstRate());
        values.put("sellingPrice", item.getSellingPrice());
        values.put("packWeightKg", item.getPackWeightKg());
        values.put("packetsPerCarton", item.getId() == null ? null
                : conversionRepository.findByItemIdAndUomCode(item.getId(), ItemService.CARTON)
                        .map(ItemUomConversion::getQtyInBase).orElse(null));
        values.put("active", item.isActive());
        return values;
    }
}
