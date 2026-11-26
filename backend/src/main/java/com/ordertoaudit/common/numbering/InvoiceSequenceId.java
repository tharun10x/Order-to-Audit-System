package com.ordertoaudit.common.numbering;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite primary key of invoice_sequence: (doc_type, financial_year). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InvoiceSequenceId implements Serializable {

    private DocType docType;
    private String financialYear;
}
