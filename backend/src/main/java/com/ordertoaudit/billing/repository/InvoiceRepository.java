package com.ordertoaudit.billing.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.entity.InvoiceStatus;

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

    /**
     * Newest first. Every filter is optional (null means any). salesmanId limits it to that salesman's customers
     * (R2.2). The casts tell PostgreSQL the type of a date left out, which it cannot work out from "? is null".
     */
    @Query("""
            select i from Invoice i
            where (:status is null or i.status = :status)
              and (cast(:from as LocalDate) is null or i.invoiceDate >= :from)
              and (cast(:to as LocalDate) is null or i.invoiceDate <= :to)
              and (:salesmanId is null or i.customer.salesman.id = :salesmanId)
            order by i.id desc""")
    List<Invoice> search(@Param("status") InvoiceStatus status, @Param("from") LocalDate from,
                         @Param("to") LocalDate to, @Param("salesmanId") Long salesmanId);
}
