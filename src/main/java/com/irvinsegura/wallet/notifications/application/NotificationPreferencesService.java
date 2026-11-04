package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.notifications.domain.model.EmailPreferences;
import com.irvinsegura.wallet.notifications.domain.port.EmailPreferencesRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferencesService {

    private final EmailPreferencesRepository repository;
    private final Clock clock;

    NotificationPreferencesService(EmailPreferencesRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EmailPreferences get(UUID userId) {
        return repository.find(userId).orElse(EmailPreferences.DEFAULT);
    }

    @Transactional
    public EmailPreferences update(UUID userId, EmailPreferences preferences) {
        repository.save(userId, preferences, clock.instant());
        return preferences;
    }
}
