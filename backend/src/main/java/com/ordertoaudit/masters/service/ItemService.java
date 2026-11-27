package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.entity.ItemUomConversion;
import com.ordertoaudit.masters.repository.BomRepository;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.repository.ItemUomConversionRepository;
import com.ordertoaudit.masters.web.ItemResponse;
import com.ordertoaudit.masters.web.StatusFilter;

/** The item list, for purchases, production runs, stock screens and the item master. */
@Service
public class ItemService {

    /** The item-specific unit that holds packets per carton (R5.1). */
    static final String CARTON = "ctn";

    private final ItemRepository itemRepository;
    private final BomRepository bomRepository;
    private final ItemUomConversionRepository conversionRepository;
    private final UnitConversionService unitConversionService;

    public ItemService(ItemRepository itemRepository, BomRepository bomRepository,
                       ItemUomConversionRepository conversionRepository, UnitConversionService unitConversionService) {
        this.itemRepository = itemRepository;
        this.bomRepository = bomRepository;
        this.conversionRepository = conversionRepository;
        this.unitConversionService = unitConversionService;
    }

    /** By default the active items (R5.4, M6), by kind then code. */
    @Transactional(readOnly = true)
    public List<ItemResponse> list(StatusFilter status) {
        return itemRepository.findAllByOrderByKindAscCodeAsc().stream()
                .filter(item -> status.includes(item.isActive()))
                .map(this::toResponse)
                .toList();
    }

    /** Call inside a transaction. */
    public ItemResponse toResponse(Item item) {
        boolean hasActiveBom = item.getKind() == ItemKind.FG
                && bomRepository.findByProductIdAndActiveTrue(item.getId()).isPresent();
        Integer packetsPerCarton = conversionRepository.findByItemIdAndUomCode(item.getId(), CARTON)
                .map(ItemUomConversion::getQtyInBase).map(qty -> qty.intValue()).orElse(null);
        return new ItemResponse(item.getCode(), item.getName(), item.getKind(), item.getBaseUom().getCode(),
                unitConversionService.allowedUnits(item), hasActiveBom, item.getReorderLevel(), item.getHsnCode(),
                item.getGstRate(), item.getSellingPrice(), item.getPackWeightKg(), packetsPerCarton, item.isActive(),
                baseUnitLockReason(item));
    }

    /**
     * M7: null while the base unit may still change. Ledger quantities and recipe quantities are stored in the
     * base unit, so once either exists a new base unit would silently change what they mean.
     */
    public String baseUnitLockReason(Item item) {
        String unit = item.getBaseUom().getCode();
        if (itemRepository.hasStockMovements(item.getId())) {
            return "The base unit stays " + unit + ": " + item.getCode()
                    + " has stock movements, and the ledger quantities are in " + unit;
        }
        if (bomRepository.isUsedInAnyBom(item.getId())) {
            return "The base unit stays " + unit + ": " + item.getCode()
                    + " is in a recipe, and recipe quantities are in " + unit;
        }
        return null;
    }
}
