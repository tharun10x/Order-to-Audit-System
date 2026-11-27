package com.ordertoaudit.masters.web;

import java.util.Locale;

import com.ordertoaudit.common.error.BadRequestException;

/** M6: ?status= on the master lists. active (the default) keeps every existing screen unchanged. */
public enum StatusFilter {
    ACTIVE,
    INACTIVE,
    ALL;

    public static StatusFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be active, inactive or all");
        }
    }

    public boolean includes(boolean active) {
        return this == ALL || (this == ACTIVE) == active;
    }
}
