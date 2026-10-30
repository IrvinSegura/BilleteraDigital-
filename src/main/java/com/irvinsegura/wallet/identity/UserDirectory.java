package com.irvinsegura.wallet.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the identity module.
 */
public interface UserDirectory {

    /**
     * Finds a user by email, ignoring case and surrounding spaces. Malformed emails simply find nobody.
     */
    Optional<UserReference> findByEmail(String email);

    /**
     * Email and name of a user, for messages addressed to them. Events only carry user ids, so listeners look the
     * contact up when they need it.
     */
    Optional<UserContact> findContact(UUID userId);
}
