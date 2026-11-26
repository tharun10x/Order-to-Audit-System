package com.ordertoaudit.common.error;

import java.util.List;

/** R9.5: an order cannot be approved while a line has a shortage. Becomes HTTP 409 with one detail per short item. */
public class OrderShortageException extends ConflictException {

    private final List<String> details;

    public OrderShortageException(String message, List<String> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
