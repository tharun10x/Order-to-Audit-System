package com.ordertoaudit.security.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.common.error.UnauthorizedException;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;
import com.ordertoaudit.security.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** The same words for an unknown user, a wrong password and an inactive user (R2.4). */
    private static final String LOGIN_FAILED = "Invalid username or password";

    private final AuthService authService;
    private final CurrentUserProvider currentUser;

    public AuthController(AuthService authService, CurrentUserProvider currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    /** The only public endpoint (R2.6). */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request).orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED));
    }

    @PreAuthorize(Roles.SIGNED_IN)
    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.current());
    }
}
