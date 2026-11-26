package com.ordertoaudit.billing.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.billing.service.DispatchService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dispatches")
public class DispatchController {

    private final DispatchService dispatchService;
    private final CurrentUserProvider currentUser;

    public DispatchController(DispatchService dispatchService, CurrentUserProvider currentUser) {
        this.dispatchService = dispatchService;
        this.currentUser = currentUser;
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DispatchResponse dispatch(@Valid @RequestBody DispatchRequest request) {
        return dispatchService.dispatch(request, currentUser.current());
    }
}
