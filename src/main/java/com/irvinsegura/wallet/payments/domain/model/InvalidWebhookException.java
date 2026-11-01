package com.irvinsegura.wallet.payments.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The webhook's signature is missing, wrong or too old, or its body cannot be read. Nothing in it is trusted.
 */
public class InvalidWebhookException extends DomainException {

    public InvalidWebhookException(String message) {
        super("INVALID_WEBHOOK", message);
    }
}
