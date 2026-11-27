package com.ordertoaudit.masters.service;

import java.util.Locale;
import java.util.regex.Pattern;

import com.ordertoaudit.common.GstStates;
import com.ordertoaudit.common.error.BadRequestException;

/** Checks shared by the customer, supplier and item masters (R5.3). Each throws a 400 that says what to fix. */
public final class MasterRules {

    /** Customer and item codes: capitals, digits, "-" and "_", as printed on documents. */
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,29}");

    /**
     * M1: the GSTIN layout. 2-digit state code, 10-character PAN (5 letters, 4 digits, 1 letter), entity number
     * (1-9 or a letter), the letter Z, a check character. The check character itself is not verified (M1).
     */
    private static final Pattern GSTIN = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");

    private MasterRules() {
    }

    /** Trimmed text, or null when blank. */
    public static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Upper case, so "murukku500" and "MURUKKU500" are the same code. */
    public static String code(String value, String what) {
        String code = text(value);
        if (code == null) {
            throw new BadRequestException("Enter a " + what + " code");
        }
        code = code.toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new BadRequestException("Code '" + code + "' must be up to 30 capital letters, digits, '-' or '_'");
        }
        return code;
    }

    /** M2: null stays null (a supplier's state is optional); otherwise a real GST state code. */
    public static String stateCode(String value) {
        String stateCode = text(value);
        if (stateCode != null && !GstStates.isKnown(stateCode)) {
            throw new BadRequestException("'" + stateCode + "' is not a GST state code (for example 33 for Tamil Nadu)");
        }
        return stateCode;
    }

    /** R5.3, M1: an optional GSTIN, upper-cased. If given, it must fit the layout and start with the state code. */
    public static String gstin(String value, String stateCode) {
        String gstin = text(value);
        if (gstin == null) {
            return null;
        }
        gstin = gstin.toUpperCase(Locale.ROOT);
        if (gstin.length() != 15) {
            throw new BadRequestException("A GSTIN has 15 characters; " + gstin + " has " + gstin.length());
        }
        if (!GSTIN.matcher(gstin).matches()) {
            throw new BadRequestException("GSTIN " + gstin + " is not in the GSTIN format "
                    + "(state code, PAN, entity number, Z, check character, for example 33AAACO1234A1Z5)");
        }
        if (stateCode == null) {
            throw new BadRequestException("Choose the state: a GSTIN must start with its state code");
        }
        if (!gstin.startsWith(stateCode)) {
            throw new BadRequestException("GSTIN " + gstin + " starts with " + gstin.substring(0, 2)
                    + ", but the state code is " + stateCode + ". The first two digits of a GSTIN are its state code");
        }
        return gstin;
    }
}
