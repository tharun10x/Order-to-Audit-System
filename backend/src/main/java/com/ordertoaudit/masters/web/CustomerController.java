package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.CustomerAdminService;
import com.ordertoaudit.masters.service.CustomerService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

@RestController
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerAdminService customerAdminService;
    private final CurrentUserProvider currentUser;

    public CustomerController(CustomerService customerService, CustomerAdminService customerAdminService,
                              CurrentUserProvider currentUser) {
        this.customerService = customerService;
        this.customerAdminService = customerAdminService;
        this.currentUser = currentUser;
    }

    /** status = active (default), inactive or all (M6). */
    @PreAuthorize(Roles.CUSTOMER_READ)
    @GetMapping("/api/customers")
    public List<CustomerResponse> list(@RequestParam(required = false) String status) {
        return customerService.list(currentUser.current(), StatusFilter.parse(status));
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/api/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerAdminService.create(request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/api/customers/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerAdminService.update(id, request, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/api/customers/{id}/deactivate")
    public CustomerResponse deactivate(@PathVariable Long id) {
        return customerAdminService.setActive(id, false, currentUser.current());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/api/customers/{id}/reactivate")
    public CustomerResponse reactivate(@PathVariable Long id) {
        return customerAdminService.setActive(id, true, currentUser.current());
    }

    /** M5: who a customer can be assigned to. */
    @PreAuthorize(Roles.ADMIN_ONLY)
    @GetMapping("/api/salesmen")
    public List<SalesmanResponse> salesmen() {
        return customerAdminService.salesmen();
    }
}
