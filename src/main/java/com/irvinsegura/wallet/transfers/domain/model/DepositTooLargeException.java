package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.shared.domain.Money;

public class DepositTooLargeException extends DomainException {

    public DepositTooLargeException(Money max) {
        super("DEPOSIT_TOO_LARGE", "A single deposit can be at most %s".formatted(max));
    }
}
