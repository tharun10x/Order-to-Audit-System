package com.ordertoaudit.security.web;

import java.time.LocalDate;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.UserRole;

/** GET /api/auth/me: the signed-in user, and the business date (A17). */
public record MeResponse(Long id, String username, String fullName, UserRole role, LocalDate businessDate) {

    public static MeResponse from(AppUser user, LocalDate businessDate) {
        return new MeResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(), businessDate);
    }
}
