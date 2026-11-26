package com.ordertoaudit.payments.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.payments.service.PaymentService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUser;

    public PaymentController(PaymentService paymentService, CurrentUserProvider currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse record(@Valid @RequestBody PaymentRequest request) {
        return paymentService.record(request, currentUser.current());
    }

    @PreAuthorize(Roles.ACCOUNTS_READ)
    @GetMapping("/payments")
    public List<PaymentResponse> list() {
        return paymentService.list();
    }

    @PreAuthorize(Roles.ACCOUNTS_READ)
    @GetMapping("/customers/{id}/outstanding")
    public CustomerOutstandingResponse outstanding(@PathVariable Long id) {
        return paymentService.customerOutstanding(id, currentUser.current());
    }
}
