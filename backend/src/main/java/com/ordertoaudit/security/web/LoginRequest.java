package com.ordertoaudit.security.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/login. The username limit is the width of audit_log.username. */
public record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank String password) {

    /** The password must never end up in a log. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
