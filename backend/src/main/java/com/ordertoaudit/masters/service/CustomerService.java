package com.ordertoaudit.masters.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.masters.repository.CustomerRepository;
import com.ordertoaudit.masters.web.CustomerResponse;

/** Read-only customer list for order taking. */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Active customers (R5.4). A salesman gets only the ones assigned to them (R2.2). */
    @Transactional(readOnly = true)
    public List<CustomerResponse> list(AppUser user) {
        List<Customer> customers = user.getRole() == UserRole.SALESMAN
                ? customerRepository.findBySalesmanIdAndActiveTrueOrderByNameAsc(user.getId())
                : customerRepository.findByActiveTrueOrderByNameAsc();
        return customers.stream().map(CustomerResponse::from).toList();
    }
}
