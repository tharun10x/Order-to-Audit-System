package com.ordertoaudit.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.payments.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** R10.6: an invoice with money from a POSTED payment cannot be cancelled. */
    @Query("""
            select count(a) > 0 from PaymentAllocation a
            where a.invoice.id = :invoiceId
              and a.payment.status = com.ordertoaudit.payments.entity.PaymentStatus.POSTED""")
    boolean hasPostedPaymentFor(@Param("invoiceId") Long invoiceId);
}
