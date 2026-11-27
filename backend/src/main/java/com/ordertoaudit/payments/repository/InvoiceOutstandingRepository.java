package com.ordertoaudit.payments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.payments.entity.InvoiceOutstanding;

public interface InvoiceOutstandingRepository extends JpaRepository<InvoiceOutstanding, Long> {

    /**
     * Selecting plain columns (not the entity) makes Hibernate run the SQL every time, so a payment sees
     * its own allocations. Empty for a cancelled invoice, because the view lists POSTED invoices only.
     */
    @Query("select o.paid as paid, o.outstanding as outstanding from InvoiceOutstanding o where o.invoiceId = :invoiceId")
    Optional<PaidSnapshot> snapshot(@Param("invoiceId") Long invoiceId);
}
