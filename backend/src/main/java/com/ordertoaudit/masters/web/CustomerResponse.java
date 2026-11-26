package com.ordertoaudit.masters.web;

import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.CustomerType;

/** A customer as needed to take an order. */
public record CustomerResponse(Long id, String code, String name, CustomerType type, String stateCode, String gstin,
                               String address, String phone) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getCode(), customer.getName(), customer.getType(),
                customer.getStateCode(), customer.getGstin(), customer.getAddress(), customer.getPhone());
    }
}
