package com.ordertoaudit.masters.web;

import com.ordertoaudit.masters.entity.CustomerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/customers and PUT /api/customers/{id}. code is required when creating and can then never
 * change (M3): on an edit leave it out or send the same code. gstin is optional (R5.3, M1).
 */
public record CustomerRequest(
        @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull CustomerType type,
        @NotBlank String stateCode,
        @Size(max = 15) String gstin,
        @Size(max = 500) String address,
        @Size(max = 15) String phone,
        @NotNull Long salesmanId) {
}
