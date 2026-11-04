package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    /**
     * Loads the token with a row lock, so two concurrent refreshes cannot both rotate it.
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    void save(RefreshToken token);

    void revokeFamily(UUID familyId, Instant now);

    /**
     * The live token of each of the user's sessions: not revoked and not expired. A session rotates its token on
     * every refresh, so it has one at most.
     */
    List<RefreshToken> findLiveByUser(UserId userId, Instant now);

    /**
     * When each of these sessions started: the issue time of its first token.
     */
    Map<UUID, Instant> sessionStarts(Collection<UUID> familyIds);

    /**
     * Ends every session of the user, e.g. after a password change or when the account is disabled.
     */
    void revokeAllForUser(UserId userId, Instant now);

    /**
     * Deletes the tokens of every family whose tokens have all expired. A family with any live token is kept whole,
     * so presenting one of its rotated tokens is still detected as reuse.
     *
     * @return how many tokens were deleted
     */
    int deleteExpiredFamilies(Instant now);
}
