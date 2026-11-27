package com.ordertoaudit.masters.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.UnitMismatchException;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemUomConversion;
import com.ordertoaudit.masters.entity.Uom;
import com.ordertoaudit.masters.entity.UomDimension;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.repository.ItemUomConversionRepository;
import com.ordertoaudit.masters.repository.UomRepository;

/**
 * Converts a quantity entered in any allowed unit into the item's base unit (R3.1 to R3.3).
 *
 * Rules, in order:
 * 1. The item's own base unit: no change.
 * 2. A unit with a row in item_uom_conversion for this item (e.g. carton of Murukku = 20 packets).
 * 3. MASS and VOLUME items: any unit of the same dimension, using uom.factor_to_base (g to kg, ml to L).
 * 4. Anything else is rejected. In particular a COUNT item (pouch, carton, packet) accepts only
 *    its base unit or a unit configured for that item, so a pouch cannot be entered in packets.
 *
 * The rules live in one place, refusal(): toBase() applies them, and allowedUnits() lists the units they accept,
 * so a screen never offers a unit that toBase() would reject.
 */
@Service
public class UnitConversionService {

    private final ItemRepository itemRepository;
    private final UomRepository uomRepository;
    private final ItemUomConversionRepository conversionRepository;

    public UnitConversionService(ItemRepository itemRepository, UomRepository uomRepository,
                                 ItemUomConversionRepository conversionRepository) {
        this.itemRepository = itemRepository;
        this.uomRepository = uomRepository;
        this.conversionRepository = conversionRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal toBase(Long itemId, BigDecimal qty, String uomCode) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BadRequestException("Unknown item id " + itemId));
        Uom entered = uomRepository.findByCode(uomCode)
                .orElseThrow(() -> new BadRequestException("Unknown unit '" + uomCode + "'"));
        Optional<ItemUomConversion> itemSpecific = conversionRepository.findByItemIdAndUomId(itemId, entered.getId());

        String refusal = refusal(item, entered, itemSpecific);
        if (refusal != null) {
            throw new UnitMismatchException(refusal);
        }
        Uom base = item.getBaseUom();
        if (entered.getId().equals(base.getId())) {
            return Money.qty(qty);                                                  // rule 1
        }
        if (itemSpecific.isPresent()) {
            return Money.qty(qty.multiply(itemSpecific.get().getQtyInBase()));      // rule 2
        }
        BigDecimal inBase = qty.multiply(entered.getFactorToBase())                 // rule 3
                .divide(base.getFactorToBase(), Money.QTY_SCALE, Money.ROUNDING);
        return Money.qty(inBase);
    }

    /** The unit codes the item accepts, base unit first: oil gives [L, ml], a pouch gives [pc]. */
    @Transactional(readOnly = true)
    public List<String> allowedUnits(Item item) {
        List<Uom> allowed = new ArrayList<>();
        for (Uom uom : uomRepository.findAll()) {
            Optional<ItemUomConversion> itemSpecific = conversionRepository.findByItemIdAndUomId(item.getId(), uom.getId());
            if (refusal(item, uom, itemSpecific) == null) {
                allowed.add(uom);
            }
        }
        Long baseId = item.getBaseUom().getId();
        allowed.sort(Comparator.comparing((Uom uom) -> !uom.getId().equals(baseId)).thenComparing(Uom::getCode));
        return allowed.stream().map(Uom::getCode).toList();
    }

    /** R3.3: null when the item accepts the unit, otherwise why not, in words. */
    private String refusal(Item item, Uom entered, Optional<ItemUomConversion> itemSpecific) {
        Uom base = item.getBaseUom();
        // 1. Already the base unit; 2. an item-specific unit such as a carton
        if (entered.getId().equals(base.getId()) || itemSpecific.isPresent()) {
            return null;
        }
        // 4. COUNT items never convert by dimension
        if (base.getDimension() == UomDimension.COUNT) {
            return "Item " + item.getCode() + " is counted in " + base.getCode() + ". Unit '" + entered.getCode()
                    + "' is not allowed for it (only " + base.getCode() + " or a unit configured for this item)";
        }
        // 3. MASS and VOLUME: same dimension converts through the unit factors
        if (entered.getDimension() != base.getDimension()
                || entered.getFactorToBase() == null || base.getFactorToBase() == null) {
            return "Unit '" + entered.getCode() + "' (" + entered.getDimension() + ") cannot be converted to "
                    + base.getCode() + " (" + base.getDimension() + ") for item " + item.getCode();
        }
        return null;
    }
}
