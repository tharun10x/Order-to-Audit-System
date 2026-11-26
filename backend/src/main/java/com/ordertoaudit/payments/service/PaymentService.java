package com.ordertoaudit.payments.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.entity.InvoiceStatus;
import com.ordertoaudit.billing.repository.InvoiceRepository;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.masters.repository.CustomerRepository;
import com.ordertoaudit.payments.entity.InvoiceOutstanding;
import com.ordertoaudit.payments.entity.InvoicePaymentStatus;
import com.ordertoaudit.payments.entity.Payment;
import com.ordertoaudit.payments.entity.PaymentAllocation;
import com.ordertoaudit.payments.entity.PaymentStatus;
import com.ordertoaudit.payments.repository.InvoiceOutstandingRepository;
import com.ordertoaudit.payments.repository.PaidSnapshot;
import com.ordertoaudit.payments.repository.PaymentRepository;
import com.ordertoaudit.payments.web.CustomerOutstandingResponse;
import com.ordertoaudit.payments.web.PaymentRequest;
import com.ordertoaudit.payments.web.PaymentResponse;

/** Payments and receivables (R12). Outstanding is always read from the invoice_outstanding view, never stored. */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceOutstandingRepository outstandingRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                          InvoiceOutstandingRepository outstandingRepository, CustomerRepository customerRepository,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.outstandingRepository = outstandingRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    /**
     * Locks the invoices BEFORE reading what is outstanding, so two payments cannot both pay the same rupees,
     * and a cancellation cannot slip in between. (Postgres READ COMMITTED: the read after the lock sees
     * payments committed while this transaction was waiting.)
     */
    @Transactional
    public PaymentResponse record(PaymentRequest request, AppUser user) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new BadRequestException("Unknown customer id " + request.customerId()));
        BigDecimal amount = requirePaise("Payment amount", request.amount());
        Map<Long, BigDecimal> allocations = checkAllocations(request.allocations(), amount);

        Map<Long, Invoice> invoices = invoiceRepository.lockAllByIdOrderById(allocations.keySet()).stream()
                .collect(Collectors.toMap(Invoice::getId, Function.identity()));

        Payment payment = new Payment();
        payment.setCustomer(customer);
        payment.setReceivedOn(request.receivedOn());
        payment.setAmount(amount);
        payment.setMode(request.mode());
        payment.setReference(request.reference());
        payment.setStatus(PaymentStatus.POSTED);
        payment.setCreatedBy(user);

        allocations.forEach((invoiceId, part) -> {
            Invoice invoice = requirePayable(invoices.get(invoiceId), invoiceId, customer, request.receivedOn(), part);
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setInvoice(invoice);
            allocation.setAmount(part);
            payment.addAllocation(allocation);
        });
        paymentRepository.saveAndFlush(payment);

        PaymentResponse response = toResponse(payment);
        auditService.log("PAYMENT", payment.getId(), AuditAction.PAYMENT, null, snapshot(response), user);
        return response;
    }

    /** All payments, most recently received first. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> list() {
        return paymentRepository.findAllByOrderByReceivedOnDescIdDesc().stream().map(this::toResponse).toList();
    }

    /** R12.4: the sum of the customer's invoice outstanding. A salesman sees only their own customers (R2.2). */
    @Transactional(readOnly = true)
    public CustomerOutstandingResponse customerOutstanding(Long customerId, AppUser user) {
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> user.getRole() != UserRole.SALESMAN || c.isAssignedTo(user))
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found"));

        List<CustomerOutstandingResponse.Invoice> invoices = new ArrayList<>();
        BigDecimal total = Money.money(BigDecimal.ZERO);
        for (InvoiceOutstanding row : outstandingRepository
                .findByCustomerIdOrderByInvoiceDateAscInvoiceIdAsc(customerId)) {
            if (row.getOutstanding().signum() > 0) {
                total = total.add(row.getOutstanding());
                invoices.add(new CustomerOutstandingResponse.Invoice(row.getInvoiceId(), row.getInvoiceNo(),
                        row.getInvoiceDate(), row.getTotal(), Money.money(row.getPaid()),
                        Money.money(row.getOutstanding()),
                        InvoicePaymentStatus.of(row.getPaid(), row.getOutstanding())));
            }
        }
        return new CustomerOutstandingResponse(customer.getId(), customer.getName(), total, invoices);
    }

    /** R12.6: each invoice once, and the parts add up exactly to the amount (no advances). */
    private Map<Long, BigDecimal> checkAllocations(List<PaymentRequest.Allocation> entered, BigDecimal amount) {
        Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentRequest.Allocation allocation : entered) {
            BigDecimal part = requirePaise("Allocation", allocation.amount());
            if (allocations.put(allocation.invoiceId(), part) != null) {
                throw new BadRequestException("Invoice " + allocation.invoiceId() + " is allocated twice");
            }
            sum = sum.add(part);
        }
        if (sum.compareTo(amount) != 0) {
            throw new BadRequestException("Allocations add up to " + Money.money(sum) + " but the payment is " + amount
                    + ". They must be equal (advances are not supported)");
        }
        return allocations;
    }

    /** R12.2: money is entered to the paisa. It is rejected, not silently rounded. */
    private BigDecimal requirePaise(String what, BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > Money.MONEY_SCALE) {
            throw new BadRequestException(what + " must be above 0, with at most 2 decimals");
        }
        return Money.money(value);
    }

    /**
     * R12.2: the customer's own POSTED invoice, dated on or before the day the money was received (A16: no
     * advances), and no more than is still outstanding on it.
     */
    private Invoice requirePayable(Invoice invoice, Long invoiceId, Customer customer, LocalDate receivedOn,
                                   BigDecimal part) {
        if (invoice == null) {
            throw new BadRequestException("Unknown invoice id " + invoiceId);
        }
        if (!invoice.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("Invoice " + invoice.getInvoiceNo() + " does not belong to " + customer.getName());
        }
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new BadRequestException("Invoice " + invoice.getInvoiceNo() + " is " + invoice.getStatus());
        }
        if (invoice.getInvoiceDate().isAfter(receivedOn)) {
            throw new BadRequestException("Invoice " + invoice.getInvoiceNo() + " is dated " + invoice.getInvoiceDate()
                    + ", after the payment was received on " + receivedOn + " (advances are not supported)");
        }
        BigDecimal outstanding = outstandingOf(invoiceId).getOutstanding();
        if (part.compareTo(outstanding) > 0) {
            throw new BadRequestException("Allocation of " + part + " is more than the outstanding "
                    + Money.money(outstanding) + " on " + invoice.getInvoiceNo());
        }
        return invoice;
    }

    private PaidSnapshot outstandingOf(Long invoiceId) {
        return outstandingRepository.snapshot(invoiceId)
                .orElseThrow(() -> new IllegalStateException("No invoice_outstanding row for invoice " + invoiceId));
    }

    private PaymentResponse toResponse(Payment payment) {
        List<PaymentResponse.Allocation> allocations = payment.getAllocations().stream()
                .map(this::toResponse)
                .toList();
        return new PaymentResponse(payment.getId(), payment.getCustomer().getId(), payment.getReceivedOn(),
                payment.getAmount(), payment.getMode(), payment.getReference(), payment.getStatus(), allocations);
    }

    /**
     * The view has no row for a cancelled invoice (possible only once a payment can be reversed and its invoice
     * then cancelled, R12.5), so outstanding and status are then null.
     */
    private PaymentResponse.Allocation toResponse(PaymentAllocation allocation) {
        Long invoiceId = allocation.getInvoice().getId();
        PaidSnapshot now = outstandingRepository.snapshot(invoiceId).orElse(null);
        return new PaymentResponse.Allocation(invoiceId, allocation.getInvoice().getInvoiceNo(),
                allocation.getAmount(),
                now == null ? null : Money.money(now.getOutstanding()),
                now == null ? null : InvoicePaymentStatus.of(now.getPaid(), now.getOutstanding()));
    }

    private Map<String, Object> snapshot(PaymentResponse payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", payment.customerId());
        data.put("receivedOn", payment.receivedOn().toString());
        data.put("amount", payment.amount());
        data.put("mode", payment.mode().name());
        data.put("reference", payment.reference());
        data.put("allocations", payment.allocations().stream()
                .map(a -> Map.<String, Object>of("invoiceNo", a.invoiceNo(), "amount", a.amount()))
                .toList());
        return data;
    }
}
