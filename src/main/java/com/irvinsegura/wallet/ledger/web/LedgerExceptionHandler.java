package com.irvinsegura.wallet.ledger.web;

import com.irvinsegura.wallet.ledger.domain.model.CapitalContributionExistsException;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationInProgressException;
import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.shared.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Ledger errors that are not plain business rule violations (422).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LedgerExceptionHandler {

    @ExceptionHandler({ReconciliationInProgressException.class, CapitalContributionExistsException.class})
    ProblemDetail handleConflict(DomainException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
    }
}
