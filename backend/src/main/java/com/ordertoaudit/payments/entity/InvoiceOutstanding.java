package com.ordertoaudit.payments.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only mapping of the invoice_outstanding view (R12.3): POSTED invoices only, less the allocations of
 * POSTED payments. Nothing is ever saved through this class.
 */
@Entity
@Immutable
@Table(name = "invoice_outstanding")
@Getter
@NoArgsConstructor
public class InvoiceOutstanding {

    @Id
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    private BigDecimal total;

    private BigDecimal paid;

    private BigDecimal outstanding;
}
