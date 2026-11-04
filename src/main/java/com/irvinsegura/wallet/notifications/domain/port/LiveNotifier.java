package com.irvinsegura.wallet.notifications.domain.port;

import com.irvinsegura.wallet.notifications.domain.model.Notification;

/**
 * Tells the user's open apps right away that a notification arrived, so they refresh without polling. Best effort:
 * an app that isn't connected finds the notification in the inbox when it next asks.
 */
public interface LiveNotifier {

    void push(Notification notification);
}
