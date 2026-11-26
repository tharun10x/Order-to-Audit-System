package com.ordertoaudit.security.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.repository.AppUserRepository;
import com.ordertoaudit.security.web.LoginRequest;
import com.ordertoaudit.security.web.LoginResponse;
import com.ordertoaudit.security.web.UserResponse;

/** Checks a username and password and writes the LOGIN or LOGIN_FAILED audit row (R14.4). */
@Service
public class AuthService {

    /** BCrypt only reads the first 72 bytes. Longer input is refused, not silently cut short. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditService auditService;
    /** Compared against when the username is unknown, so that answer takes as long as a wrong password. */
    private final String dummyHash;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("no user has this password");
    }

    /**
     * Empty means the login failed, for any reason. It is returned, not thrown: an exception would roll this
     * transaction back and the LOGIN_FAILED row with it.
     */
    @Transactional
    public Optional<LoginResponse> login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username()).orElse(null);
        boolean passwordMatches = matches(request.password(), user == null ? dummyHash : user.getPasswordHash());

        if (user == null || !passwordMatches || !user.isActive()) {
            auditService.logFailedLogin(request.username());
            return Optional.empty();
        }
        TokenService.IssuedToken token = tokenService.issue(user);
        auditService.log("USER", user.getId(), AuditAction.LOGIN, null, Map.of("role", user.getRole().name()), user);
        return Optional.of(new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user)));
    }

    private boolean matches(String password, String hash) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && passwordEncoder.matches(password, hash);
    }
}
