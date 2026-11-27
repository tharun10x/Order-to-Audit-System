package com.ordertoaudit.billing.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ordertoaudit.billing.entity.InvoiceStatus;
import com.ordertoaudit.billing.entity.SupplyType;
import com.ordertoaudit.payments.entity.InvoicePaymentStatus;
import com.ordertoaudit.payments.entity.PaymentMode;
import com.ordertoaudit.payments.entity.PaymentStatus;

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
        /** CGST + SGST + IGST, as stored on the invoice. */
        BigDecimal taxTotal,
        BigDecimal total,
        InvoiceStatus status,
        String creditNoteNo,
        String dispatchNo,
        LocalDate dispatchDate,
        BigDecimal paid,
        BigDecimal outstanding,
        InvoicePaymentStatus paymentStatus,
        List<Payment> payments,
        List<Line> lines) {

    /** A payment allocated to this invoice: amount is the part that settled this invoice. Reversed ones are kept. */
    public record Payment(Long paymentId, LocalDate receivedOn, PaymentMode mode, String reference, BigDecimal amount,
                          PaymentStatus status) {
    }

    /** For the buyer, stateCode is the place of supply. */
    public record Party(String name, String gstin, String stateCode, String address) {
    }

    public record Line(String itemCode, String itemName, String hsnCode, BigDecimal qty, BigDecimal rate,
                       BigDecimal taxableValue, BigDecimal gstRate, BigDecimal cgst, BigDecimal sgst,
                       BigDecimal igst) {
    }
}
