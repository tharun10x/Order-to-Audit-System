package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.ItemService;
import com.ordertoaudit.security.Roles;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PreAuthorize(Roles.OPERATIONS_READ)
    @GetMapping
    public List<ItemResponse> list() {
        return itemService.list();
    }
}
