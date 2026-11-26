package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.CustomerService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CurrentUserProvider currentUser;

    public CustomerController(CustomerService customerService, CurrentUserProvider currentUser) {
        this.customerService = customerService;
        this.currentUser = currentUser;
    }

    @PreAuthorize(Roles.ORDER_ENTRY)
    @GetMapping
    public List<CustomerResponse> list() {
        return customerService.list(currentUser.current());
    }
}
