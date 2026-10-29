package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    /**
     * Loads the token with a row lock, so two concurrent refreshes cannot both rotate it.
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    void save(RefreshToken token);

    void revokeFamily(UUID familyId, Instant now);
}
