package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class NotificationNotFoundException extends DomainException {

    public NotificationNotFoundException() {
        super("NOTIFICATION_NOT_FOUND", "Notification not found");
    }
}
