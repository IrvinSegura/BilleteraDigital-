package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.BankAccountNotFoundException;
import com.irvinsegura.wallet.accounts.WalletNotFoundException;
import com.irvinsegura.wallet.accounts.domain.model.BankAccountAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.model.MovementNotFoundException;
import com.irvinsegura.wallet.accounts.domain.model.WalletAlreadyExistsException;
import com.irvinsegura.wallet.shared.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Accounts errors that are not plain business rule violations (422). Applies to every controller, so other
 * modules calling the {@code Wallets} API get the same responses.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccountsExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    ProblemDetail handleWalletNotFound(WalletNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(MovementNotFoundException.class)
    ProblemDetail handleMovementNotFound(MovementNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(BankAccountNotFoundException.class)
    ProblemDetail handleBankAccountNotFound(BankAccountNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(BankAccountAlreadyExistsException.class)
    ProblemDetail handleBankAccountAlreadyExists(BankAccountAlreadyExistsException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    ProblemDetail handleWalletAlreadyExists(WalletAlreadyExistsException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
    }
}
