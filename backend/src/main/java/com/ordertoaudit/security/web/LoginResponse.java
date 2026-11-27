package com.ordertoaudit.security.web;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Send the token as "Authorization: Bearer <token>" until expiresAt. businessDate is "today" for the business
 * (A17): the date screens should default to, which can differ from the device's date during a demo.
 */
public record LoginResponse(String token, Instant expiresAt, LocalDate businessDate, UserResponse user) {
}
