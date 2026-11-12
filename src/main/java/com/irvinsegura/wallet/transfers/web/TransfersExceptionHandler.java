package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.shared.web.Problems;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentNotPendingException;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentSelfApprovalException;
import com.irvinsegura.wallet.transfers.domain.model.DepositNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.TransferNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Transfers errors that are not plain business rule violations (422).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TransfersExceptionHandler {

    @ExceptionHandler({TransferNotFoundException.class, DepositNotFoundException.class,
            WithdrawalNotFoundException.class, AdjustmentNotFoundException.class})
    ProblemDetail handleNotFound(DomainException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    // Another administrator decided it first
    @ExceptionHandler(AdjustmentNotPendingException.class)
    ProblemDetail handleNotPending(AdjustmentNotPendingException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Already decided", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(AdjustmentSelfApprovalException.class)
    ProblemDetail handleSelfApproval(AdjustmentSelfApprovalException ex) {
        return Problems.create(HttpStatus.FORBIDDEN, "Second approval required", ex.getMessage(), ex.getCode());
    }
}
