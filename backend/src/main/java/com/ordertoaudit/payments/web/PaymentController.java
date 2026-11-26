package com.ordertoaudit.payments.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.payments.service.PaymentService;
import com.ordertoaudit.security.CurrentUserProvider;

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

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse record(@Valid @RequestBody PaymentRequest request) {
        return paymentService.record(request, currentUser.current());
    }

    @GetMapping("/customers/{id}/outstanding")
    public CustomerOutstandingResponse outstanding(@PathVariable Long id) {
        return paymentService.customerOutstanding(id, currentUser.current());
    }
}
