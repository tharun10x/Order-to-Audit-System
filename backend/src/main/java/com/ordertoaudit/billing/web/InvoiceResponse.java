package com.ordertoaudit.billing.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ordertoaudit.billing.entity.InvoiceStatus;
import com.ordertoaudit.billing.entity.SupplyType;
import com.ordertoaudit.payments.entity.InvoicePaymentStatus;

/**
 * One invoice. seller, buyer and the lines are the copies taken when it was raised (R10.5).
 * paid, outstanding and paymentStatus are null for a cancelled invoice: nothing is receivable on it.
 */
public record InvoiceResponse(
        Long id,
        String invoiceNo,
        LocalDate invoiceDate,
        Long orderId,
        String orderNo,
        Long customerId,
        Party seller,
        Party buyer,
        String placeOfSupply,
        SupplyType supplyType,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal total,
        InvoiceStatus status,
        String creditNoteNo,
        String dispatchNo,
        BigDecimal paid,
        BigDecimal outstanding,
        InvoicePaymentStatus paymentStatus,
        List<Line> lines) {

    /** For the buyer, stateCode is the place of supply. */
    public record Party(String name, String gstin, String stateCode, String address) {
    }

    public record Line(String itemCode, String itemName, String hsnCode, BigDecimal qty, BigDecimal rate,
                       BigDecimal taxableValue, BigDecimal gstRate, BigDecimal cgst, BigDecimal sgst,
                       BigDecimal igst) {
    }
}
