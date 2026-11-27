package com.ordertoaudit.masters.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.masters.repository.AppUserRepository;
import com.ordertoaudit.masters.repository.CustomerRepository;
import com.ordertoaudit.masters.web.CustomerRequest;
import com.ordertoaudit.masters.web.CustomerResponse;
import com.ordertoaudit.masters.web.SalesmanResponse;

/**
 * The Admin's customer master (R5.3, R5.4). Customers are deactivated, never deleted. A deactivated customer
 * cannot be ordered for (SalesOrderService); its existing orders, invoices and payments stay as they are.
 * Every change is audited with the values before and after (R14).
 */
@Service
public class CustomerAdminService {

    private static final String ENTITY = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public CustomerAdminService(CustomerRepository customerRepository, AppUserRepository userRepository,
                                CustomerService customerService, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    /** M5: the salesmen a customer can be assigned to. */
    @Transactional(readOnly = true)
    public List<SalesmanResponse> salesmen() {
        return userRepository.findByRoleAndActiveTrueOrderByFullNameAsc(UserRole.SALESMAN).stream()
                .map(SalesmanResponse::from).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request, AppUser user) {
        String code = MasterRules.code(request.code(), "customer");
        if (customerRepository.existsByCode(code)) {
            throw new ConflictException("Customer code " + code + " is already used");
        }
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setActive(true);
        apply(customer, request);
        customerRepository.save(customer);
        auditService.log(ENTITY, customer.getId(), AuditAction.CREATE, null, snapshot(customer), user);
        return customerService.toResponse(customer);
    }

    /** M3: everything except the code can change. */
    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request, AppUser user) {
        Customer customer = find(id);
        if (request.code() != null && !MasterRules.code(request.code(), "customer").equals(customer.getCode())) {
            throw new BadRequestException("A customer's code cannot be changed (it is " + customer.getCode() + ")");
        }
        Map<String, Object> before = snapshot(customer);
        apply(customer, request);
        auditService.log(ENTITY, customer.getId(), AuditAction.UPDATE, before, snapshot(customer), user);
        return customerService.toResponse(customer);
    }

    /** R5.4: deactivate or reactivate. Asking for the state it is already in changes nothing. */
    @Transactional
    public CustomerResponse setActive(Long id, boolean active, AppUser user) {
        Customer customer = find(id);
        if (customer.isActive() != active) {
            Map<String, Object> before = snapshot(customer);
            customer.setActive(active);
            auditService.log(ENTITY, customer.getId(), AuditAction.UPDATE, before, snapshot(customer), user);
        }
        return customerService.toResponse(customer);
    }

    private Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    private void apply(Customer customer, CustomerRequest request) {
        String stateCode = MasterRules.stateCode(request.stateCode());
        customer.setName(MasterRules.text(request.name()));
        customer.setType(request.type());
        customer.setStateCode(stateCode);
        customer.setGstin(MasterRules.gstin(request.gstin(), stateCode));
        customer.setAddress(MasterRules.text(request.address()));
        customer.setPhone(MasterRules.text(request.phone()));
        customer.setSalesman(activeSalesman(request.salesmanId()));
    }

    /** R5.3, M5: a customer is looked after by an active salesman. */
    private AppUser activeSalesman(Long salesmanId) {
        AppUser salesman = userRepository.findById(salesmanId).orElse(null);
        if (salesman == null || salesman.getRole() != UserRole.SALESMAN || !salesman.isActive()) {
            throw new BadRequestException("The assigned salesman must be an active user with the Salesman role");
        }
        return salesman;
    }

    private static Map<String, Object> snapshot(Customer customer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", customer.getCode());
        values.put("name", customer.getName());
        values.put("type", customer.getType().name());
        values.put("stateCode", customer.getStateCode());
        values.put("gstin", customer.getGstin());
        values.put("address", customer.getAddress());
        values.put("phone", customer.getPhone());
        values.put("salesman", customer.getSalesman() == null ? null : customer.getSalesman().getUsername());
        values.put("active", customer.isActive());
        return values;
    }
}
