package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.ItemAdminService;
import com.ordertoaudit.masters.service.ItemService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;
    private final ItemAdminService itemAdminService;
    private final CurrentUserProvider currentUser;

    public ItemController(ItemService itemService, ItemAdminService itemAdminService, CurrentUserProvider currentUser) {
        this.itemService = itemService;
        this.itemAdminService = itemAdminService;
        this.currentUser = currentUser;
    }

    /** status = active (default), inactive or all (M6). */
    @PreAuthorize(Roles.OPERATIONS_READ)
    @GetMapping
    public List<ItemResponse> list(@RequestParam(required = false) String status) {
        return itemService.list(StatusFilter.parse(status));
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@Valid @RequestBody ItemRequest request) {
        return itemAdminService.create(request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/{code}")
    public ItemResponse update(@PathVariable String code, @Valid @RequestBody ItemRequest request) {
        return itemAdminService.update(code, request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{code}/deactivate")
    public ItemResponse deactivate(@PathVariable String code) {
        return itemAdminService.setActive(code, false, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{code}/reactivate")
    public ItemResponse reactivate(@PathVariable String code) {
        return itemAdminService.setActive(code, true, currentUser.current());
    }
}
