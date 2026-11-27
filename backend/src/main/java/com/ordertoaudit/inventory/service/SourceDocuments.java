package com.ordertoaudit.inventory.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * R6.2: what document each stock movement came from, in words, for the stock ledger screen and the stock ledger
 * report alike (one query, so they always show the same text). invoiceId is set for a dispatch and a credit note,
 * so a screen can link to the invoice. A REVERSAL keeps the source of the document it undoes (R6), so it shows
 * that document too.
 */
@Component
public class SourceDocuments {

    public record SourceDocument(String documentNo, Long invoiceId) {
    }

    private static final String QUERY = """
            SELECT m.id AS movement_id,
                   CASE m.source_type
                       WHEN 'PURCHASE'       THEN 'Bill ' || p.bill_no || ' (' || s.name || ')'
                       WHEN 'PRODUCTION_RUN' THEN r.run_no
                       WHEN 'DISPATCH'       THEN d.dispatch_no || ' / ' || di.invoice_no
                       WHEN 'CREDIT_NOTE'    THEN cn.note_no
                       WHEN 'ADJUSTMENT'     THEN 'Adjustment ' || m.source_id
                       ELSE 'Opening stock'
                   END AS document_no,
                   CASE m.source_type
                       WHEN 'DISPATCH'    THEN d.invoice_id
                       WHEN 'CREDIT_NOTE' THEN cn.invoice_id
                   END AS invoice_id
            FROM stock_movement m
            LEFT JOIN purchase p        ON m.source_type = 'PURCHASE'       AND p.id = m.source_id
            LEFT JOIN supplier s        ON s.id = p.supplier_id
            LEFT JOIN production_run r  ON m.source_type = 'PRODUCTION_RUN' AND r.id = m.source_id
            LEFT JOIN dispatch d        ON m.source_type = 'DISPATCH'       AND d.id = m.source_id
            LEFT JOIN invoice di        ON di.id = d.invoice_id
            LEFT JOIN credit_note cn    ON m.source_type = 'CREDIT_NOTE'    AND cn.id = m.source_id
            WHERE m.id IN (:movementIds)""";

    private record Row(Long movementId, String documentNo, Long invoiceId) {
    }

    private final JdbcClient jdbc;

    public SourceDocuments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Per movement id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, SourceDocument> forMovements(Collection<Long> movementIds) {
        Map<Long, SourceDocument> documents = new HashMap<>();
        if (movementIds.isEmpty()) {
            return documents;
        }
        for (Row row : jdbc.sql(QUERY).param("movementIds", movementIds).query(Row.class).list()) {
            documents.put(row.movementId(), new SourceDocument(row.documentNo(), row.invoiceId()));
        }
        return documents;
    }
}
