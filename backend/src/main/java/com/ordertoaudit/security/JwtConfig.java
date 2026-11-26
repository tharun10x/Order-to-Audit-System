package com.ordertoaudit.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** HS256 tokens: this application both signs and checks them with the same secret. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(properties)));
    }

    /**
     * Accepts only HS256 signed with our key. Then checks: it has an expiry, it has not expired, and its user
     * may still act (ActiveUserValidator).
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, ActiveUserValidator activeUserValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtClaimValidator<>("exp", Objects::nonNull),
                // No clock skew: the same server's clock writes and reads the expiry (the default allows 60 s)
                new JwtTimestampValidator(Duration.ZERO),
                activeUserValidator));
        return decoder;
    }

    /** BCrypt, cost 10, matching the seeded hashes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private SecretKey key(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
