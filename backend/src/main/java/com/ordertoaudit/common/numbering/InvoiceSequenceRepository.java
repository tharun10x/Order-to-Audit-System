package com.ordertoaudit.common.numbering;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, InvoiceSequenceId> {

    /**
     * Creates the counter row for a new financial year. ON CONFLICT DO NOTHING makes it safe when two
     * transactions try at the same time: the second one simply finds the row already there.
     */
    @Modifying
    @Query(value = "INSERT INTO invoice_sequence (doc_type, financial_year, prefix) "
            + "VALUES (:docType, :financialYear, :prefix) ON CONFLICT DO NOTHING", nativeQuery = true)
    void insertIfMissing(@Param("docType") String docType, @Param("financialYear") String financialYear,
                         @Param("prefix") String prefix);

    /** SELECT ... FOR UPDATE: whoever holds the lock is the only one who can take the next number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSequence s where s.docType = :docType and s.financialYear = :financialYear")
    Optional<InvoiceSequence> lockByDocTypeAndFinancialYear(@Param("docType") DocType docType,
                                                            @Param("financialYear") String financialYear);
}
