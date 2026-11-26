package com.ordertoaudit.purchase.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.purchase.service.PurchaseService;
import com.ordertoaudit.security.CurrentUserProvider;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final CurrentUserProvider currentUser;

    public PurchaseController(PurchaseService purchaseService, CurrentUserProvider currentUser) {
        this.purchaseService = purchaseService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse record(@Valid @RequestBody PurchaseRequest request) {
        return purchaseService.record(request, currentUser.current());
    }
}
