package com.irvinsegura.wallet.identity;

import java.util.Optional;

/**
 * Public API of the identity module.
 */
public interface UserDirectory {

    /**
     * Finds a user by email, ignoring case and surrounding spaces. Malformed emails simply find nobody.
     */
    Optional<UserReference> findByEmail(String email);
}
