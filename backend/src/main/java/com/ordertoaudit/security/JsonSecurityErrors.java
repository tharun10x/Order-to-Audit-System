package com.ordertoaudit.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ordertoaudit.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * The 401 and 403 answers of the security filters, in the same ErrorResponse shape as every other error.
 * They never say why a token was refused.
 */
@Component
public class JsonSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrors(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: no token, or one that is invalid or expired. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "A valid login token is required");
    }

    /** 403: logged in, but the role may not do this. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Your role is not allowed to do this");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message, List.of());
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }
}
