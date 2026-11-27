package com.ordertoaudit.masters.web;

import com.ordertoaudit.masters.entity.Supplier;

/** A supplier, to pick on a purchase and to edit in the masters. */
public record SupplierResponse(Long id, String name, String gstin, String stateCode, String phone, boolean active) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getGstin(), supplier.getStateCode(),
                supplier.getPhone(), supplier.isActive());
    }
}
