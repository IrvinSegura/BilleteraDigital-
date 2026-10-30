package com.irvinsegura.wallet.notifications.domain.port;

import com.irvinsegura.wallet.notifications.domain.model.EmailMessage;

public interface EmailSender {

    /**
     * Sends the email or throws, so the caller's event publication fails and is retried later.
     */
    void send(EmailMessage message);
}
