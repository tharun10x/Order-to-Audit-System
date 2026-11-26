package com.ordertoaudit.common.error;

/** The request clashes with existing data (duplicate bill number, ...). Becomes HTTP 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
