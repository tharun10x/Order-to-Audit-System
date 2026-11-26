package com.ordertoaudit.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.billing.entity.CreditNote;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Optional<CreditNote> findByInvoiceId(Long invoiceId);
}
