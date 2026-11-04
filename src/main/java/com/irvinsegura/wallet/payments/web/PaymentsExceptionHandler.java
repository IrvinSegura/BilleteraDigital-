package com.irvinsegura.wallet.payments.web;

import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransactionNotFoundException;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransactionNotUnmatchedException;
import com.irvinsegura.wallet.shared.web.Problems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Runs before the global handler, which would answer 422 for these domain exceptions.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaymentsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentsExceptionHandler.class);

    // Raised while making a deposit, so it reaches users through the transfers endpoints too
    @ExceptionHandler(PaymentProviderUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProviderUnavailable(PaymentProviderUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(Problems.create(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider unavailable",
                        ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(ProviderTransactionNotFoundException.class)
    ProblemDetail handleProviderTransactionNotFound(ProviderTransactionNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Provider transaction not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(ProviderTransactionNotUnmatchedException.class)
    ProblemDetail handleProviderTransactionNotUnmatched(ProviderTransactionNotUnmatchedException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Provider transaction not unmatched", ex.getMessage(),
                ex.getCode());
    }

    // The reason stays in the logs; the caller is either Stripe, which never reads it, or someone forging requests
    @ExceptionHandler(InvalidWebhookException.class)
    ProblemDetail handleInvalidWebhook(InvalidWebhookException ex) {
        log.warn("Rejected a payment webhook: {}", ex.getMessage());
        return Problems.create(HttpStatus.BAD_REQUEST, "Invalid webhook", "The webhook could not be verified",
                ex.getCode());
    }
}
