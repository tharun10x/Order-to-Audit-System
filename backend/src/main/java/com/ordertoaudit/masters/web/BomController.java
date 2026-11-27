package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.BomService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

/** Recipes (R5.6 to R5.10). Production, Auditor and Owner read them; only the Admin changes them. */
@RestController
@RequestMapping("/api/boms")
public class BomController {

    private final BomService bomService;
    private final CurrentUserProvider currentUser;

    public BomController(BomService bomService, CurrentUserProvider currentUser) {
        this.bomService = bomService;
        this.currentUser = currentUser;
    }

    @PreAuthorize(Roles.OPERATIONS_READ)
    @GetMapping
    public List<BomResponse> versions(@RequestParam String productCode) {
        return bomService.versions(productCode);
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BomResponse create(@Valid @RequestBody BomRequest request) {
        return bomService.create(request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/activate")
    public BomResponse activate(@PathVariable Long id) {
        return bomService.activate(id, currentUser.current());
    }
}
