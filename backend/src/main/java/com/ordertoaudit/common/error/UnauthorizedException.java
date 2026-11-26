package com.ordertoaudit.common.error;

/** The caller is not (or no longer) a user who may do this. Becomes HTTP 401. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
