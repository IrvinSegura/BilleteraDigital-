package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailNotVerifiedException;
import com.irvinsegura.wallet.identity.IdentityNotVerifiedException;
import com.irvinsegura.wallet.identity.PasswordConfirmationRequiredException;
import com.irvinsegura.wallet.identity.TwoFactorRequiredException;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.AdminTwoFactorRequiredException;
import com.irvinsegura.wallet.identity.domain.model.PasswordChangeRequiredException;
import com.irvinsegura.wallet.identity.domain.model.AccountCannotBeClosedException;
import com.irvinsegura.wallet.identity.domain.model.AccountClosedException;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.EmailVerificationRequiredException;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyVerifiedException;
import com.irvinsegura.wallet.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationConflictException;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationLimitException;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationNotFoundException;
import com.irvinsegura.wallet.identity.domain.model.InvalidIdentityDocumentException;
import com.irvinsegura.wallet.identity.domain.model.InvalidRefreshTokenException;
import com.irvinsegura.wallet.identity.domain.model.RoleChangeBlockedException;
import com.irvinsegura.wallet.identity.domain.model.TwoFactorAlreadyEnabledException;
import com.irvinsegura.wallet.identity.domain.model.TwoFactorNotEnabledException;
import com.irvinsegura.wallet.identity.domain.model.SessionNotFoundException;
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
 * Identity errors that are not plain business rule violations (422). Runs before the global handler and applies to
 * every controller, so other modules calling {@code UserDirectory} get the same responses.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    ProblemDetail handleAuthenticationFailure(DomainException ex) {
        return Problems.create(HttpStatus.UNAUTHORIZED, "Authentication failed", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler({EmailAlreadyRegisteredException.class, EmailAlreadyVerifiedException.class,
            TwoFactorAlreadyEnabledException.class, TwoFactorNotEnabledException.class})
    ProblemDetail handleConflict(DomainException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
    }

    // Authenticated, but this account is not allowed to do it
    @ExceptionHandler({AccountDisabledException.class, AccountClosedException.class, EmailNotVerifiedException.class,
            EmailVerificationRequiredException.class, TwoFactorRequiredException.class, PasswordConfirmationRequiredException.class,
            AdminTwoFactorRequiredException.class, IdentityNotVerifiedException.class,
            PasswordChangeRequiredException.class})
    ProblemDetail handleAccountNotAllowed(DomainException ex) {
        return Problems.create(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), ex.getCode());
    }

    // The reason tells the app what the owner has to do first, e.g. withdraw what is left
    @ExceptionHandler(AccountCannotBeClosedException.class)
    ProblemDetail handleCannotClose(AccountCannotBeClosedException ex) {
        ProblemDetail problem = Problems.create(HttpStatus.UNPROCESSABLE_CONTENT, "Account cannot be closed",
                ex.getMessage(), ex.getCode());
        problem.setProperty("reason", ex.reason());
        return problem;
    }

    @ExceptionHandler(RoleChangeBlockedException.class)
    ProblemDetail handleRoleChangeBlocked(RoleChangeBlockedException ex) {
        ProblemDetail problem = Problems.create(HttpStatus.UNPROCESSABLE_CONTENT, "Role change blocked",
                ex.getMessage(), ex.getCode());
        problem.setProperty("reason", ex.reason());
        return problem;
    }

    // The reason says which field or picture to fix
    @ExceptionHandler(InvalidIdentityDocumentException.class)
    ProblemDetail handleInvalidDocument(InvalidIdentityDocumentException ex) {
        ProblemDetail problem = Problems.create(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid identity document",
                ex.getMessage(), ex.getCode());
        problem.setProperty("reason", ex.reason());
        return problem;
    }

    @ExceptionHandler(IdentityVerificationConflictException.class)
    ProblemDetail handleVerificationConflict(IdentityVerificationConflictException ex) {
        ProblemDetail problem = Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
        problem.setProperty("reason", ex.reason());
        return problem;
    }

    @ExceptionHandler(IdentityVerificationLimitException.class)
    ProblemDetail handleVerificationLimit(IdentityVerificationLimitException ex) {
        return Problems.create(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(IdentityVerificationNotFoundException.class)
    ProblemDetail handleVerificationNotFound(IdentityVerificationNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail handleSessionNotFound(SessionNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
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
