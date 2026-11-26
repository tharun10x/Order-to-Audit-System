package com.ordertoaudit.common.error;

/** The request itself is wrong (bad unit, unknown item, rule violated). Becomes HTTP 400. */
public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super(message);
    }
}
