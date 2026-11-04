package com.irvinsegura.wallet.notifications.infrastructure.email;

import com.irvinsegura.wallet.notifications.domain.model.EmailMessage;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends through the mail server configured in {@code spring.mail.*}: Gmail locally and in production, Mailpit in
 * integration tests.
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
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
