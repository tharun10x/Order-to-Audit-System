package com.ordertoaudit.reports.service;

import java.util.Map;

/**
 * The GST "unique quantity code" (UQC) for the HSN summary, from the item's base unit (F7). The UQC list is
 * fixed by GST law, like the state codes, so it is not master data.
 */
public final class GstUnits {

    private static final Map<String, String> UQC = Map.of(
            "pkt", "PAC", "kg", "KGS", "g", "GMS", "L", "LTR", "ml", "MLT", "pc", "PCS", "ctn", "CTN");

    private GstUnits() {
    }

    /** "OTH" (others) for a unit without a matching code. */
    public static String uqc(String unitCode) {
        return UQC.getOrDefault(unitCode, "OTH");
    }
}
