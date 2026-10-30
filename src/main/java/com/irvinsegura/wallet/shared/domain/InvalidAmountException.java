package com.irvinsegura.wallet.shared.domain;

public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String message) {
        super("INVALID_AMOUNT", message);
    }
}
