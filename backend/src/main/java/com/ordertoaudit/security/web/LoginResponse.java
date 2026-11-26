package com.ordertoaudit.security.web;

import java.time.Instant;

/** Send the token as "Authorization: Bearer <token>" until expiresAt. */
public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
}
