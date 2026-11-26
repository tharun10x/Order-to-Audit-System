package com.ordertoaudit.common.error;

import java.util.List;

/** The JSON body of every error: {status, error, message, details[]}. */
public record ErrorResponse(int status, String error, String message, List<String> details) {
}
