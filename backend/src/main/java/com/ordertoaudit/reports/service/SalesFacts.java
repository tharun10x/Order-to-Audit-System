package com.ordertoaudit.reports.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sales documents of a period, read from the invoice's own copied details (R10.5). Used by the sales
 * register, the GST summary and my-sales, so they all treat credit notes the same way (R15.3):
 *  - an invoice counts in the period of its invoice date, at its original amounts, even if later cancelled;
 *  - its credit note counts NEGATIVE in the period of the note date. A credit note always cancels the whole
 *    invoice (R10.6), so its lines are the invoice's lines with the sign reversed.
 * Amounts are stored positive; sign is +1 for an invoice and -1 for a credit note.
 */
@Component
public class SalesFacts {

    /** One invoice or credit note. documentNo is the invoice no or the note no; invoiceNo is always the invoice's. */
    public record Document(String kind, int sign, Long invoiceId, String documentNo, LocalDate documentDate,
                           String invoiceNo, Long customerId, String buyerName, String buyerGstin,
                           String placeOfSupply, String supplyType, BigDecimal taxableValue, BigDecimal cgst,
                           BigDecimal sgst, BigDecimal igst, BigDecimal total, String status) {

        public boolean isCreditNote() {
            return sign < 0;
        }

        public BigDecimal signed(BigDecimal amount) {
            return sign < 0 ? amount.negate() : amount;
        }
    }

    /** One product line of an invoice, or of the invoice a credit note cancels. */
    public record Line(int sign, Long invoiceId, String documentNo, LocalDate documentDate, String invoiceNo,
                       Long customerId, String buyerName, String buyerGstin, String placeOfSupply, String supplyType,
                       String itemCode, String itemName, String hsnCode, String unitCode, BigDecimal gstRate,
                       BigDecimal qty, BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {

        public boolean isCreditNote() {
            return sign < 0;
        }

        public boolean isRegistered() {
            return buyerGstin != null;
        }

        public BigDecimal signed(BigDecimal amount) {
            return sign < 0 ? amount.negate() : amount;
        }
    }

    private static final String DOCUMENTS = """
            SELECT 'INVOICE' AS kind, 1 AS sign, i.id AS invoice_id, i.invoice_no AS document_no,
                   i.invoice_date AS document_date, i.invoice_no, i.customer_id, i.buyer_name, i.buyer_gstin,
                   i.place_of_supply, i.supply_type, i.taxable_value, i.cgst, i.sgst, i.igst, i.total, i.status
            FROM invoice i
            WHERE i.invoice_date BETWEEN :from AND :to
            UNION ALL
            SELECT 'CREDIT_NOTE', -1, i.id, c.note_no, c.note_date, i.invoice_no, i.customer_id, i.buyer_name,
                   i.buyer_gstin, i.place_of_supply, c.supply_type, c.taxable_value, c.cgst, c.sgst, c.igst, c.total,
                   'CREDIT NOTE'
            FROM credit_note c
            JOIN invoice i ON i.id = c.invoice_id
            WHERE c.note_date BETWEEN :from AND :to
            ORDER BY document_date, document_no""";

    private static final String LINES = """
            SELECT 1 AS sign, i.id AS invoice_id, i.invoice_no AS document_no, i.invoice_date AS document_date,
                   i.invoice_no, i.customer_id, i.buyer_name, i.buyer_gstin, i.place_of_supply, i.supply_type,
                   it.code AS item_code, l.item_name, l.hsn_code, u.code AS unit_code, l.gst_rate, l.qty,
                   l.taxable_value, l.cgst, l.sgst, l.igst
            FROM invoice_line l
            JOIN invoice i ON i.id = l.invoice_id
            JOIN item it ON it.id = l.item_id
            JOIN uom u ON u.id = it.base_uom_id
            WHERE i.invoice_date BETWEEN :from AND :to
            UNION ALL
            SELECT -1, i.id, c.note_no, c.note_date, i.invoice_no, i.customer_id, i.buyer_name, i.buyer_gstin,
                   i.place_of_supply, i.supply_type, it.code, l.item_name, l.hsn_code, u.code, l.gst_rate, l.qty,
                   l.taxable_value, l.cgst, l.sgst, l.igst
            FROM credit_note c
            JOIN invoice i ON i.id = c.invoice_id
            JOIN invoice_line l ON l.invoice_id = i.id
            JOIN item it ON it.id = l.item_id
            JOIN uom u ON u.id = it.base_uom_id
            WHERE c.note_date BETWEEN :from AND :to
            ORDER BY document_date, document_no, item_code""";

    private final JdbcClient jdbc;

    public SalesFacts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Document> documents(ReportPeriod period) {
        return jdbc.sql(DOCUMENTS).param("from", period.from()).param("to", period.to())
                .query(Document.class).list();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Line> lines(ReportPeriod period) {
        return jdbc.sql(LINES).param("from", period.from()).param("to", period.to())
                .query(Line.class).list();
    }
}
