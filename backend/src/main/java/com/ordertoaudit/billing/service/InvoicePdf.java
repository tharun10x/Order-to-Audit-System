package com.ordertoaudit.billing.service;

/** A rendered invoice. */
public record InvoicePdf(String fileName, byte[] content) {
}
