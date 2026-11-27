package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Company;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.masters.repository.CompanyRepository;
import com.ordertoaudit.masters.repository.CustomerRepository;
import com.ordertoaudit.masters.web.CustomerResponse;
import com.ordertoaudit.masters.web.StatusFilter;

/** The customer list, for order taking and for the masters screen. */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;

    public CustomerService(CustomerRepository customerRepository, CompanyRepository companyRepository) {
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
    }

    /** By default the active customers (R5.4, M6). A salesman gets only the ones assigned to them (R2.2). */
    @Transactional(readOnly = true)
    public List<CustomerResponse> list(AppUser user, StatusFilter status) {
        List<Customer> customers = user.getRole() == UserRole.SALESMAN
                ? customerRepository.findBySalesmanIdOrderByNameAsc(user.getId())
                : customerRepository.findAllByOrderByNameAsc();
        String companyState = companyStateCode();
        return customers.stream()
                .filter(customer -> status.includes(customer.isActive()))
                .map(customer -> CustomerResponse.from(customer, companyState))
                .toList();
    }

    /** Call inside a transaction. */
    public CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.from(customer, companyStateCode());
    }

    private String companyStateCode() {
        return companyRepository.findAll().stream().findFirst()
                .map(Company::getStateCode)
                .orElseThrow(() -> new IllegalStateException("The company row is missing"));
    }
}
