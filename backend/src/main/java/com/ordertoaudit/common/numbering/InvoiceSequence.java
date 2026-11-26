package com.ordertoaudit.common.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The counter row for one document type in one financial year. Locked FOR UPDATE to issue a number. */
@Entity
@Table(name = "invoice_sequence")
@IdClass(InvoiceSequenceId.class)
@Getter
@Setter
@NoArgsConstructor
public class InvoiceSequence {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocType docType;

    @Id
    @Column(name = "financial_year")
    private String financialYear;

    private String prefix;

    @Column(name = "next_number")
    private int nextNumber;
}
