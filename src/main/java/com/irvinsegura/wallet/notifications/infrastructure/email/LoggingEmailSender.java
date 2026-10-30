package com.irvinsegura.wallet.notifications.infrastructure.email;

import com.irvinsegura.wallet.notifications.domain.model.EmailMessage;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For environments without a mail server. Logs only the subject: addresses and bodies are personal data.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Email delivery is disabled; not sending \"{}\"", message.subject());
    }
}
