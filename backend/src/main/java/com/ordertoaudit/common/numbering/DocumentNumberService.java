package com.ordertoaudit.common.numbering;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues document numbers like INV/2026-27/0001, CN/2026-27/0001 and RUN/2026-27/0001 (R10.2, R8).
 *
 * The number comes from a counter row that is locked FOR UPDATE inside the caller's transaction.
 * If that transaction rolls back, the counter rolls back with it, so numbers have no gaps
 * (a database SEQUENCE would skip a number on every rollback). Each financial year starts at 0001.
 *
 * MANDATORY: the number must be taken inside the transaction that saves the document.
 */
@Service
public class DocumentNumberService {

    private final InvoiceSequenceRepository repository;

    public DocumentNumberService(InvoiceSequenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocType docType, LocalDate documentDate) {
        String financialYear = financialYear(documentDate);

        repository.insertIfMissing(docType.name(), financialYear, docType.getPrefix());
        InvoiceSequence counter = repository.lockByDocTypeAndFinancialYear(docType, financialYear)
                .orElseThrow(() -> new IllegalStateException("Counter row missing for " + docType + " " + financialYear));

        int number = counter.getNextNumber();
        counter.setNextNumber(number + 1);
        return String.format("%s/%s/%04d", counter.getPrefix(), financialYear, number);
    }

    /** The Indian financial year runs 1 April to 31 March: 2026-11-03 is "2026-27", 2027-03-31 is also "2026-27". */
    public static String financialYear(LocalDate date) {
        int startYear = (date.getMonthValue() >= 4) ? date.getYear() : date.getYear() - 1;
        return String.format("%d-%02d", startYear, (startYear + 1) % 100);
    }
}
