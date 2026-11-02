package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The payout provider could not be reached. Never shown to users: the withdrawal stays pending and is sent again.
 */
public class PayoutProviderUnavailableException extends DomainException {

    public PayoutProviderUnavailableException() {
        super("PAYOUT_PROVIDER_UNAVAILABLE", "The payout provider is not available");
    }
}
