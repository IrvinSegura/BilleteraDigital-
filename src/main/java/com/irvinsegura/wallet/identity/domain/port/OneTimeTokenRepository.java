package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.OneTimeToken;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;

public interface OneTimeTokenRepository {

    void save(OneTimeToken token);

    /**
     * Loads the token with a row lock, so two requests using the same link at once cannot both succeed.
     */
    Optional<OneTimeToken> findByTokenHashForUpdate(String tokenHash);

    /**
     * Deletes the user's tokens for that purpose: when one is used, or when a new link replaces the old ones.
     */
    void deleteByUser(UserId userId, TokenPurpose purpose);

    /**
     * @return how many tokens were deleted
     */
    int deleteExpired(Instant now);
}
