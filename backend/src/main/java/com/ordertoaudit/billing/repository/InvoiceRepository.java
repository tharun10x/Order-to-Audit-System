package com.ordertoaudit.billing.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.billing.entity.Invoice;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Only the order id, without loading the invoice. Callers lock the order first and load the invoice
     * afterwards, so they never act on a copy read before the lock.
     */
    @Query("select i.order.id from Invoice i where i.id = :id")
    Optional<Long> findOrderId(@Param("id") Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> lockById(@Param("id") Long id);

    /** Ascending id order, so two payments covering the same invoices cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id in :ids order by i.id asc")
    List<Invoice> lockAllByIdOrderById(@Param("ids") Collection<Long> ids);

    List<Invoice> findAllByOrderByIdDesc();

    /** R2.2: sales for the customers assigned to one salesman. */
    List<Invoice> findByCustomerSalesmanIdOrderByIdDesc(Long salesmanId);
}
