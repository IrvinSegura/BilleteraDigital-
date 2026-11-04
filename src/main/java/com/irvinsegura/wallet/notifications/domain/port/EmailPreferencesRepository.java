package com.irvinsegura.wallet.notifications.domain.port;

import com.irvinsegura.wallet.notifications.domain.model.EmailPreferences;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailPreferencesRepository {

    /**
     * The user's choices; empty if they never changed the defaults.
     */
    Optional<EmailPreferences> find(UUID userId);

    void save(UUID userId, EmailPreferences preferences, Instant now);
}
