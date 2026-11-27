package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.repository.SupplierRepository;
import com.ordertoaudit.masters.web.StatusFilter;
import com.ordertoaudit.masters.web.SupplierResponse;

/** The supplier list, for recording purchases and for the masters screen. */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /** By default the active suppliers (R5.4, M6), by name. */
    @Transactional(readOnly = true)
    public List<SupplierResponse> list(StatusFilter status) {
        return supplierRepository.findAllByOrderByNameAsc().stream()
                .filter(supplier -> status.includes(supplier.isActive()))
                .map(SupplierResponse::from)
                .toList();
    }
}
