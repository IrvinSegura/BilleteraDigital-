package com.irvinsegura.wallet.notifications.web;

import com.irvinsegura.wallet.notifications.domain.model.NotificationNotFoundException;
import com.irvinsegura.wallet.shared.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Notifications errors that are not plain business rule violations (422).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationsExceptionHandler {

    // Other users' notifications get the same answer, so ids cannot be probed
    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail handleNotificationNotFound(NotificationNotFoundException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }
}
