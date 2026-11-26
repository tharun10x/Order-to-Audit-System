package com.ordertoaudit.common.numbering;

/** invoice_sequence.doc_type: the kinds of numbered documents, each with its number prefix. */
public enum DocType {
    INVOICE("INV"),
    CREDIT_NOTE("CN"),
    PRODUCTION_RUN("RUN"),
    SALES_ORDER("SO"),
    DISPATCH("DC");

    private final String prefix;

    DocType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
