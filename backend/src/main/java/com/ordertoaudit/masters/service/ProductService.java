package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.entity.ItemUomConversion;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.repository.ItemUomConversionRepository;
import com.ordertoaudit.masters.web.ProductResponse;

/** The price list: active finished goods. */
@Service
public class ProductService {

    private static final String CARTON_UNIT = "ctn";

    private final ItemRepository itemRepository;
    private final ItemUomConversionRepository conversionRepository;

    public ProductService(ItemRepository itemRepository, ItemUomConversionRepository conversionRepository) {
        this.itemRepository = itemRepository;
        this.conversionRepository = conversionRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return itemRepository.findByKindAndActiveTrueOrderByNameAsc(ItemKind.FG).stream()
                .map(this::toResponse)
                .toList();
    }

    private ProductResponse toResponse(Item item) {
        // R3.4: packets per carton is master data in item_uom_conversion
        return new ProductResponse(item.getCode(), item.getName(), item.getSellingPrice(), item.getHsnCode(),
                item.getGstRate(), conversionRepository.findByItemIdAndUomCode(item.getId(), CARTON_UNIT)
                        .map(ItemUomConversion::getQtyInBase)
                        .orElse(null));
    }
}
