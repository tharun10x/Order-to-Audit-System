package com.ordertoaudit.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ota.security.jwt.* (R2.8). The checks run while the application starts, so a missing or weak secret stops
 * it before it can serve a single request.
 */
@ConfigurationProperties("ota.security.jwt")
public record JwtProperties(String secret, @DefaultValue("8h") Duration expiry) {

    /** HS256 needs a key at least as long as its 256-bit hash. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Set JWT_SECRET to at least " + MIN_SECRET_BYTES
                    + " bytes. Only the dev and test profiles have a default");
        }
        if (expiry.isZero() || expiry.isNegative()) {
            throw new IllegalStateException("ota.security.jwt.expiry must be positive");
        }
    }

    /** The secret must never end up in a log. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, expiry=" + expiry + "]";
    }
}
