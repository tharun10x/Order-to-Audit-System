package com.ordertoaudit.masters.web;

import com.ordertoaudit.masters.entity.Supplier;

/** An active supplier, to pick on a purchase. */
public record SupplierResponse(Long id, String name, String gstin, String stateCode) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getGstin(), supplier.getStateCode());
    }
}
