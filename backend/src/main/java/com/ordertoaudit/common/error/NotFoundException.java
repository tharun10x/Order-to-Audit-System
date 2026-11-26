package com.ordertoaudit.common.error;

/** A record asked for by id does not exist. Becomes HTTP 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
