package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.wallet.identity.domain.model.InvalidRefreshTokenException;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.shared.web.Problems;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Identity errors that are not plain business rule violations (422). Runs before the global handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    ProblemDetail handleAuthenticationFailure(DomainException ex) {
        return Problems.create(HttpStatus.UNAUTHORIZED, "Authentication failed", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<ProblemDetail> handleAccountLocked(AccountLockedException ex) {
        // An HTTP date rather than seconds, so the value stays correct however long the response takes to arrive
        String retryAfter =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ex.getLockedUntil().atOffset(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header(HttpHeaders.RETRY_AFTER, retryAfter)
                .body(Problems.create(HttpStatus.LOCKED, "Account locked", ex.getMessage(), ex.getCode()));
    }
}
