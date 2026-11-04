package com.irvinsegura.wallet.payments.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Only a movement the application could not match waits for a person's decision.
 */
public class ProviderTransactionNotUnmatchedException extends DomainException {

    public ProviderTransactionNotUnmatchedException() {
        super("PROVIDER_TRANSACTION_NOT_UNMATCHED", "Only an unmatched provider transaction can be resolved");
    }
}
