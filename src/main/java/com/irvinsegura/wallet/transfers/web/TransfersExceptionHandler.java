package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.Problems;
import com.irvinsegura.wallet.transfers.domain.model.TransferNotFoundException;
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

    @ExceptionHandler(TransferNotFoundException.class)
    ProblemDetail handleTransferNotFound(TransferNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }
}
