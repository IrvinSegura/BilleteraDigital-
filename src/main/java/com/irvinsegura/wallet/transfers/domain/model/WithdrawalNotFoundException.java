package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class WithdrawalNotFoundException extends DomainException {

    public WithdrawalNotFoundException() {
        super("WITHDRAWAL_NOT_FOUND", "Withdrawal not found");
    }
}
