package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.repository.SupplierRepository;
import com.ordertoaudit.masters.web.SupplierResponse;

/** Read-only supplier list for recording purchases. */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /** Active suppliers (R5.4), by name. */
    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return supplierRepository.findByActiveTrueOrderByNameAsc().stream().map(SupplierResponse::from).toList();
    }
}
