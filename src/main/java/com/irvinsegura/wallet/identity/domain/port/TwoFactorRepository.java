package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.TwoFactor;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Authenticator apps and their recovery codes.
 */
public interface TwoFactorRepository {

    Optional<TwoFactor> findByUserId(UserId userId);

    /**
     * Locks the row until the transaction ends, so a code accepted at the same moment by two requests is used once.
     */
    Optional<TwoFactor> findByUserIdForUpdate(UserId userId);

    void save(TwoFactor twoFactor);

    /**
     * Removes the app and its recovery codes.
     */
    void delete(UserId userId);

    /**
     * Replaces every recovery code with these hashes.
     */
    void replaceRecoveryCodes(UserId userId, List<String> codeHashes, Instant now);

    /**
     * Marks the code used. Returns false if it does not exist or was used before.
     */
    boolean useRecoveryCode(UserId userId, String codeHash, Instant now);

    int countUnusedRecoveryCodes(UserId userId);
}
