package com.ordertoaudit.billing.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.entity.InvoiceLine;
import com.ordertoaudit.billing.entity.SupplyType;

/**
 * What the invoice template prints, already formatted as text. Built ONLY from the invoice and its lines, so a
 * reprint is not affected by later edits to the company, customer or item records (R10.5).
 * creditNoteNo is null unless the invoice is cancelled.
 */
public record InvoicePrintView(
        String invoiceNo,
        String invoiceDate,
        String creditNoteNo,
        String sellerName,
        String sellerGstin,
        String sellerState,
        String sellerAddress,
        String buyerName,
        String buyerGstin,
        String buyerAddress,
        String placeOfSupply,
        boolean intra,
        List<Line> lines,
        String taxableValue,
        String cgst,
        String sgst,
        String igst,
        String total,
        String amountInWords) {

    public record Line(String itemName, String hsnCode, String qty, String rate, String taxableValue, String gstRate,
                       String cgst, String sgst, String igst) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    /** Call inside a transaction: the lines are loaded lazily. */
    public static InvoicePrintView of(Invoice invoice, String creditNoteNo) {
        List<Line> lines = new ArrayList<>();
        for (InvoiceLine line : invoice.getLines()) {
            lines.add(new Line(line.getItemName(), line.getHsnCode(), plain(line.getQty()), money(line.getRate()),
                    money(line.getTaxableValue()), plain(line.getGstRate()) + "%",
                    money(line.getCgst()), money(line.getSgst()), money(line.getIgst())));
        }
        return new InvoicePrintView(invoice.getInvoiceNo(), invoice.getInvoiceDate().format(DATE), creditNoteNo,
                invoice.getSellerName(), invoice.getSellerGstin(),
                GstStates.describe(invoice.getSellerStateCode()), invoice.getSellerAddress(),
                invoice.getBuyerName(),
                invoice.getBuyerGstin() == null ? "Unregistered" : invoice.getBuyerGstin(),
                invoice.getBuyerAddress(), GstStates.describe(invoice.getPlaceOfSupply()),
                invoice.getSupplyType() == SupplyType.INTRA, lines,
                money(invoice.getTaxableValue()), money(invoice.getCgst()), money(invoice.getSgst()),
                money(invoice.getIgst()), money(invoice.getTotal()), AmountInWords.of(invoice.getTotal()));
    }

    private static String money(BigDecimal amount) {
        return IndianNumberFormat.money(amount);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
