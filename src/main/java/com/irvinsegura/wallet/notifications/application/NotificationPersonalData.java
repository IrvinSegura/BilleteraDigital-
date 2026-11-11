package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.identity.PersonalDataSource;
import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Every notification still in the inbox, newest first.
 */
@Component
class NotificationPersonalData implements PersonalDataSource {

    private static final int PAGE_SIZE = 100;

    private final NotificationRepository repository;

    NotificationPersonalData(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String section() {
        return "notifications";
    }

    @Override
    public Object collect(UUID userId) {
        List<Notification> all = new ArrayList<>();
        NotificationRepository.NotificationSlice slice;
        int page = 0;
        do {
            slice = repository.findByUser(userId, false, page++, PAGE_SIZE);
            all.addAll(slice.items());
        } while (slice.hasNext());
        return all;
    }
}
