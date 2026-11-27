package com.ordertoaudit.masters.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/suppliers and PUT /api/suppliers/{id}. The state is optional, but needed with a GSTIN (M4). */
public record SupplierRequest(
        @NotBlank @Size(max = 150) String name,
        String stateCode,
        @Size(max = 15) String gstin,
        @Size(max = 15) String phone) {
}
