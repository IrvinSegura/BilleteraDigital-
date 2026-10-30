package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;
import java.util.Currency;

/**
 * One error for "no such user" and "user without a wallet in that currency", so the response says as little as
 * possible about which emails are registered.
 */
public class RecipientNotFoundException extends DomainException {

    public RecipientNotFoundException(Currency currency) {
        super("RECIPIENT_NOT_FOUND",
                "No recipient with a %s wallet was found for that email".formatted(currency.getCurrencyCode()));
    }
}
