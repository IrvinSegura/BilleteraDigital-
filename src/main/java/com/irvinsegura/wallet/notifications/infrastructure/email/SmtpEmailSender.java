package com.irvinsegura.wallet.notifications.infrastructure.email;

import com.irvinsegura.wallet.notifications.domain.model.EmailMessage;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Sends through the mail server configured in {@code spring.mail.*}: Gmail locally and in production, Mailpit in
 * integration tests. Each email carries both forms, and the client shows the best one it can.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        mailSender.send(mime -> {
            MimeMessageHelper mail = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body(), message.html());
        });
    }
}
