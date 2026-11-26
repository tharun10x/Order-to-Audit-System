package com.ordertoaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY: open access for dev and test while the backend is built.
 * Replaced by JWT + role-based rules in roadmap step 6 (business rules R2.1).
 */
@Configuration
@Profile({"dev", "test"})
public class DevSecurityConfig {

    @Bean
    SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}