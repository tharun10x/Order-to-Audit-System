package com.ordertoaudit.common.error;

/** The caller may not do this with this record (R2.2). Becomes HTTP 403. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
