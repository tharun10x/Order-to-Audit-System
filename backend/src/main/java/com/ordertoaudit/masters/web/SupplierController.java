package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.SupplierService;
import com.ordertoaudit.security.Roles;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /** For whoever records purchases. */
    @PreAuthorize(Roles.PURCHASE_ENTRY)
    @GetMapping
    public List<SupplierResponse> list() {
        return supplierService.list();
    }
}
