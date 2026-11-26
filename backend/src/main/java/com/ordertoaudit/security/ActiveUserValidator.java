package com.ordertoaudit.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.ordertoaudit.masters.repository.AppUserRepository;

/**
 * R2.4, R2.7: a token stays valid only while its user is active and still has the role written in it.
 * It runs on every request, so deactivating a user takes effect at once and not when the token expires.
 * The price is one query per request.
 */
@Component
public class ActiveUserValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NO_LONGER_VALID =
            new OAuth2Error("invalid_token", "The user is inactive or the role has changed", null);

    private final AppUserRepository userRepository;

    public ActiveUserValidator(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Long userId = userId(token.getSubject());
        boolean stillValid = userId != null && userRepository.findById(userId)
                .filter(user -> user.isActive() && user.getRole().name().equals(token.getClaimAsString("role")))
                .isPresent();
        return stillValid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(NO_LONGER_VALID);
    }

    /** The subject is the user id. Anything else is not one of our tokens. */
    private Long userId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
