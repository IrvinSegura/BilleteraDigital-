package com.irvinsegura.wallet.shared.web;

import com.irvinsegura.wallet.shared.domain.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 Problem Details body with a stable {@code code} and the request's correlation id.
 * Internal details such as stack traces never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomainException(DomainException ex) {
        // Codes are a small fixed set, so they are safe as a tag; a jump in e.g. INSUFFICIENT_FUNDS is worth seeing
        meterRegistry.counter("wallet.business.rejections", "code", ex.getCode()).increment();
        return Problems.create(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violation", ex.getMessage(),
                ex.getCode());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
        ProblemDetail problem = Problems.create(HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Request limit reached; retry in %d s".formatted(ex.retryAfterSeconds()), "RATE_LIMITED");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problem);
    }

    // Also reached from the security filter chain, which delegates its 401 and 403 responses here
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return Problems.create(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required",
                "UNAUTHORIZED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return Problems.create(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action",
                "FORBIDDEN");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return Problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", "INTERNAL_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(violations, headers, status);
    }

    // Controllers with constraints on a header or path variable are validated as a whole method, so an invalid body
    // arrives here instead of in handleMethodArgumentNotValid; it answers the same way
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(error ->
                        violations.add(new FieldViolation(error.getField(), error.getDefaultMessage())));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error ->
                        violations.add(new FieldViolation(parameter, error.getDefaultMessage())));
            }
        }
        return validationFailed(violations, headers, status);
    }

    private static ResponseEntity<Object> validationFailed(List<FieldViolation> violations, HttpHeaders headers,
            HttpStatusCode status) {
        ProblemDetail problem = Problems.create(status, "Invalid request", "Request validation failed",
                "VALIDATION_FAILED");
        problem.setProperty("violations", violations);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    // Spring's built-in errors (404, 405, malformed JSON...) also get a code and correlation id
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            Problems.withCode(problem, status != null ? status.name() : "HTTP_" + statusCode.value());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    record FieldViolation(String field, String message) {
    }
}
