package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.repository.BomRepository;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.web.ItemResponse;

/** Read-only item list for purchases, production runs and stock screens. */
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final BomRepository bomRepository;
    private final UnitConversionService unitConversionService;

    public ItemService(ItemRepository itemRepository, BomRepository bomRepository,
                       UnitConversionService unitConversionService) {
        this.itemRepository = itemRepository;
        this.bomRepository = bomRepository;
        this.unitConversionService = unitConversionService;
    }

    /** Active items (R5.4), by kind then code. */
    @Transactional(readOnly = true)
    public List<ItemResponse> list() {
        return itemRepository.findByActiveTrueOrderByKindAscCodeAsc().stream().map(this::toResponse).toList();
    }

    private ItemResponse toResponse(Item item) {
        boolean hasActiveBom = item.getKind() == ItemKind.FG
                && bomRepository.findByProductIdAndActiveTrue(item.getId()).isPresent();
        return new ItemResponse(item.getCode(), item.getName(), item.getKind(), item.getBaseUom().getCode(),
                unitConversionService.allowedUnits(item), hasActiveBom);
    }
}
