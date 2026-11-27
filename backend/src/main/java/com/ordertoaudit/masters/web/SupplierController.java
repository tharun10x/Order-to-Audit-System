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

import com.ordertoaudit.masters.service.SupplierAdminService;
import com.ordertoaudit.masters.service.SupplierService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierAdminService supplierAdminService;
    private final CurrentUserProvider currentUser;

    public SupplierController(SupplierService supplierService, SupplierAdminService supplierAdminService,
                              CurrentUserProvider currentUser) {
        this.supplierService = supplierService;
        this.supplierAdminService = supplierAdminService;
        this.currentUser = currentUser;
    }

    /** For whoever records purchases. status = active (default), inactive or all (M6). */
    @PreAuthorize(Roles.PURCHASE_ENTRY)
    @GetMapping
    public List<SupplierResponse> list(@RequestParam(required = false) String status) {
        return supplierService.list(StatusFilter.parse(status));
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierAdminService.create(request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierAdminService.update(id, request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/deactivate")
    public SupplierResponse deactivate(@PathVariable Long id) {
        return supplierAdminService.setActive(id, false, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/reactivate")
    public SupplierResponse reactivate(@PathVariable Long id) {
        return supplierAdminService.setActive(id, true, currentUser.current());
    }
}
