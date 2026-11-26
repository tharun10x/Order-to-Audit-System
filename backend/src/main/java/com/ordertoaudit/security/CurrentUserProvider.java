package com.ordertoaudit.security;

import org.springframework.stereotype.Component;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.repository.AppUserRepository;

/**
 * TEMPORARY, like DevSecurityConfig: until JWT login exists (roadmap step 6) every request acts as the
 * seeded admin user. Step 6 replaces this with the authenticated principal.
 */
@Component
public class CurrentUserProvider {

    private static final String DEV_USERNAME = "admin";

    private final AppUserRepository userRepository;

    public CurrentUserProvider(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser current() {
        return userRepository.findByUsername(DEV_USERNAME)
                .orElseThrow(() -> new IllegalStateException("Dev user '" + DEV_USERNAME + "' is missing from app_user"));
    }
}
