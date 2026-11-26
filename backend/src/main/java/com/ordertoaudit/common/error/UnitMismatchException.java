package com.ordertoaudit.common.error;

/** A unit that cannot be converted to the item's base unit (R3.3). */
public class UnitMismatchException extends BadRequestException {

    public UnitMismatchException(String message) {
        super(message);
    }
}
