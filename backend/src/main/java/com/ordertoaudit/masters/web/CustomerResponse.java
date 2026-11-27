package com.ordertoaudit.masters.web;

import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.CustomerType;

/**
 * A customer as needed to take an order. intraState is true when the customer is in the factory's state, so the
 * invoice will carry CGST + SGST; false means IGST (R10.3). The server decides it, so the screen never needs the
 * factory's state code. salesmanId / salesmanName: who looks after the customer (R2.2), null if nobody yet.
 */
public record CustomerResponse(Long id, String code, String name, CustomerType type, String stateCode, String gstin,
                               String address, String phone, boolean intraState, Long salesmanId,
                               String salesmanName, boolean active) {

    /** Call inside a transaction: the salesman is loaded lazily. */
    public static CustomerResponse from(Customer customer, String companyStateCode) {
        return new CustomerResponse(customer.getId(), customer.getCode(), customer.getName(), customer.getType(),
                customer.getStateCode(), customer.getGstin(), customer.getAddress(), customer.getPhone(),
                customer.getStateCode().equals(companyStateCode),
                customer.getSalesman() == null ? null : customer.getSalesman().getId(),
                customer.getSalesman() == null ? null : customer.getSalesman().getFullName(),
                customer.isActive());
    }
}
