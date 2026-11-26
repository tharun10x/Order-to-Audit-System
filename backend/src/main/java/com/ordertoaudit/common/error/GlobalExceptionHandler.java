package com.ordertoaudit.common.error;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns exceptions into clear 400 (bad input) and 409 (conflict with current data) responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return body(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableJson(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "Missing request parameter: " + e.getParameterName(), List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> wrongParameterType(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + e.getName() + "'", List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    /** One detail line per short item, so the caller sees the whole shortage list. */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> insufficientStock(InsufficientStockException e) {
        List<String> details = e.getShortages().stream().map(InsufficientStockException.Shortage::describe).toList();
        return body(HttpStatus.CONFLICT, e.getMessage(), details);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    /** A database constraint (for example a unique key) refused the data. The SQL text is not shown to the caller. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> constraint(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "The request conflicts with existing data", List.of());
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message, details));
    }
}
