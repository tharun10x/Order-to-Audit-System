package com.ordertoaudit.masters.web;

import com.ordertoaudit.masters.entity.AppUser;

/** An active salesman, to assign a customer to (M5). */
public record SalesmanResponse(Long id, String username, String fullName) {

    public static SalesmanResponse from(AppUser user) {
        return new SalesmanResponse(user.getId(), user.getUsername(), user.getFullName());
    }
}
