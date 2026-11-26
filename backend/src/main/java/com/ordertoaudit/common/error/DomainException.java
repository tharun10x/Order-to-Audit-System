package com.ordertoaudit.common.error;

/** Base class for business-rule errors. RuntimeException, so a failed posting rolls the transaction back. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
