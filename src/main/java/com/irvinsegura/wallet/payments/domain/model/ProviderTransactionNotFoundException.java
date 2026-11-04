package com.irvinsegura.wallet.payments.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class ProviderTransactionNotFoundException extends DomainException {

    public ProviderTransactionNotFoundException() {
        super("PROVIDER_TRANSACTION_NOT_FOUND", "Provider transaction not found");
    }
}
