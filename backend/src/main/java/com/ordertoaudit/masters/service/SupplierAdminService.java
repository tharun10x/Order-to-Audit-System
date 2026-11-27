package com.ordertoaudit.masters.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Supplier;
import com.ordertoaudit.masters.repository.SupplierRepository;
import com.ordertoaudit.masters.web.SupplierRequest;
import com.ordertoaudit.masters.web.SupplierResponse;

/**
 * The Admin's supplier master (R5.4). Suppliers are deactivated, never deleted; an inactive supplier cannot be
 * used on a new purchase (PurchaseService). The table has no code, so two ACTIVE suppliers may not share a name (M4).
 */
@Service
public class SupplierAdminService {

    private static final String ENTITY = "SUPPLIER";

    private final SupplierRepository supplierRepository;
    private final AuditService auditService;

    public SupplierAdminService(SupplierRepository supplierRepository, AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request, AppUser user) {
        Supplier supplier = new Supplier();
        supplier.setActive(true);
        apply(supplier, request);
        supplierRepository.save(supplier);
        auditService.log(ENTITY, supplier.getId(), AuditAction.CREATE, null, snapshot(supplier), user);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request, AppUser user) {
        Supplier supplier = find(id);
        Map<String, Object> before = snapshot(supplier);
        apply(supplier, request);
        auditService.log(ENTITY, supplier.getId(), AuditAction.UPDATE, before, snapshot(supplier), user);
        return SupplierResponse.from(supplier);
    }

    /** R5.4: deactivate or reactivate. Asking for the state it is already in changes nothing. */
    @Transactional
    public SupplierResponse setActive(Long id, boolean active, AppUser user) {
        Supplier supplier = find(id);
        if (supplier.isActive() != active) {
            if (active) {
                checkNameFree(supplier.getName(), supplier.getId());
            }
            Map<String, Object> before = snapshot(supplier);
            supplier.setActive(active);
            auditService.log(ENTITY, supplier.getId(), AuditAction.UPDATE, before, snapshot(supplier), user);
        }
        return SupplierResponse.from(supplier);
    }

    private Supplier find(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> new NotFoundException("Supplier " + id + " not found"));
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        String name = MasterRules.text(request.name());
        if (supplier.isActive()) {
            checkNameFree(name, supplier.getId());
        }
        String stateCode = MasterRules.stateCode(request.stateCode());
        supplier.setName(name);
        supplier.setStateCode(stateCode);
        supplier.setGstin(MasterRules.gstin(request.gstin(), stateCode));
        supplier.setPhone(MasterRules.text(request.phone()));
    }

    /** M4: id is the supplier itself (null when new), so keeping its own name is fine. */
    private void checkNameFree(String name, Long id) {
        if (supplierRepository.activeNameTaken(name, id)) {
            throw new ConflictException("An active supplier is already called " + name);
        }
    }

    private static Map<String, Object> snapshot(Supplier supplier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", supplier.getName());
        values.put("stateCode", supplier.getStateCode());
        values.put("gstin", supplier.getGstin());
        values.put("phone", supplier.getPhone());
        values.put("active", supplier.isActive());
        return values;
    }
}
