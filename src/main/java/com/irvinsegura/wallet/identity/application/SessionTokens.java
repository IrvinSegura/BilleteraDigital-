package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.RefreshTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.AccessTokenIssuer;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Issues the access and refresh token pair of a session, in the caller's transaction.
 */
@Component
class SessionTokens {

    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenPolicy refreshTokenPolicy;

    SessionTokens(AccessTokenIssuer accessTokenIssuer, RefreshTokenRepository refreshTokenRepository,
            RefreshTokenPolicy refreshTokenPolicy) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenPolicy = refreshTokenPolicy;
    }

    /**
     * Starts a new login family.
     */
    AuthTokens startSession(User user, Instant now) {
        return issue(user, UUID.randomUUID(), now);
    }

    AuthTokens issue(User user, UUID familyId, Instant now) {
        AccessToken accessToken = accessTokenIssuer.issue(user, now);
        RefreshToken.Issued refreshToken =
                RefreshToken.issue(user.id(), familyId, now, refreshTokenPolicy.timeToLive());
        refreshTokenRepository.save(refreshToken.token());
        return new AuthTokens(
                accessToken.value(),
                Duration.between(now, accessToken.expiresAt()),
                refreshToken.rawValue(),
                refreshTokenPolicy.timeToLive());
    }
}
