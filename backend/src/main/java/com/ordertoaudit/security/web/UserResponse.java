package com.ordertoaudit.security.web;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.UserRole;

/** A user as shown to the client. Never includes the password hash. */
public record UserResponse(Long id, String username, String fullName, UserRole role) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole());
    }
}
