package com.ordertoaudit.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.billing.entity.Dispatch;

public interface DispatchRepository extends JpaRepository<Dispatch, Long> {

    /** At most one: an order is dispatched in full, once (R11.4). */
    Optional<Dispatch> findByInvoiceId(Long invoiceId);
}
