package com.ordertoaudit.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ordertoaudit.common.error.UnauthorizedException;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.repository.AppUserRepository;

/** The AppUser behind the current request, loaded by the token's subject (the user id). */
@Component
public class CurrentUserProvider {

    private final AppUserRepository userRepository;

    public CurrentUserProvider(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new UnauthorizedException("Not logged in");
        }
        // ActiveUserValidator has already accepted the token, so the subject is a user id.
        // The user is checked again in case it was deactivated since (R2.4)
        return userRepository.findById(Long.valueOf(token.getToken().getSubject()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException("This user is no longer active"));
    }
}
