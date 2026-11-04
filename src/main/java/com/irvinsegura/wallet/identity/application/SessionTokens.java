package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.RefreshTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.AccessTokenIssuer;
import com.irvinsegura.wallet.identity.domain.port.ClientInfoSource;
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
    private final SecondFactorVerifier secondFactorVerifier;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenPolicy refreshTokenPolicy;
    private final ClientInfoSource clientInfoSource;

    SessionTokens(AccessTokenIssuer accessTokenIssuer, SecondFactorVerifier secondFactorVerifier,
            RefreshTokenRepository refreshTokenRepository, RefreshTokenPolicy refreshTokenPolicy,
            ClientInfoSource clientInfoSource) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.secondFactorVerifier = secondFactorVerifier;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenPolicy = refreshTokenPolicy;
        this.clientInfoSource = clientInfoSource;
    }

    /**
     * Starts a new login family.
     */
    AuthTokens startSession(User user, Instant now) {
        return issue(user, UUID.randomUUID(), now);
    }

    AuthTokens issue(User user, UUID familyId, Instant now) {
        // Sessions started before two-factor was turned on were ended then, so every session of a user who has it
        // passed it at login
        AccessToken accessToken =
                accessTokenIssuer.issue(user, secondFactorVerifier.isEnabled(user.id()), familyId, now);
        // Each refresh records the client again, so the session list shows where it was last used
        RefreshToken.Issued refreshToken = RefreshToken.issue(user.id(), familyId, now,
                refreshTokenPolicy.timeToLive(), clientInfoSource.current());
        refreshTokenRepository.save(refreshToken.token());
        return new AuthTokens(
                accessToken.value(),
                Duration.between(now, accessToken.expiresAt()),
                refreshToken.rawValue(),
                refreshTokenPolicy.timeToLive());
    }
}
