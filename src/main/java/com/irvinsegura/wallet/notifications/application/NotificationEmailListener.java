package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.notifications.domain.model.EmailMessage;
import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the email copy of each notification. If sending fails, the publication fails and the retry job tries again
 * with backoff. Delivery is at least once: a crash between the mail server accepting the email and the publication
 * being completed sends it again.
 */
@Component
class NotificationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailListener.class);

    private final NotificationRepository notificationRepository;
    private final UserDirectory users;
    private final EmailSender emailSender;

    NotificationEmailListener(NotificationRepository notificationRepository, UserDirectory users,
            EmailSender emailSender) {
        this.notificationRepository = notificationRepository;
        this.users = users;
        this.emailSender = emailSender;
    }

    @ApplicationModuleListener
    void on(NotificationCreated event) {
        Optional<Notification> notification = notificationRepository.findById(event.notificationId());
        Optional<UserContact> contact = users.findContact(event.userId());
        // A retry after the retention period finds nothing left to send
        if (notification.isEmpty() || contact.isEmpty()) {
            log.warn("Email for notification {} skipped: notification or user not found", event.notificationId());
            return;
        }
        emailSender.send(EmailMessage.of(notification.get(), contact.get().email(), contact.get().fullName()));
    }
}
