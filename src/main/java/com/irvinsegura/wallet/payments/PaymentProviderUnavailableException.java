package com.irvinsegura.wallet.payments;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class PaymentProviderUnavailableException extends DomainException {

    public PaymentProviderUnavailableException() {
        super("PAYMENT_PROVIDER_UNAVAILABLE", "The payment provider is not available, try again later");
    }
}
