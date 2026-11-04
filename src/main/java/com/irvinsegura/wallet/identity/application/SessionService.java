package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.SessionsRevoked;
import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.SessionNotFoundException;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's own signed-in sessions, to review them and end the ones they don't recognize. Ending a session stops
 * its refresh token at once; its access token keeps working until it expires, minutes later.
 */
@Service
public class SessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SessionService(RefreshTokenRepository refreshTokenRepository, ApplicationEventPublisher events, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The user's live sessions, the most recently used first.
     *
     * @param currentSessionId the session making the request; {@code null} if unknown
     */
    @Transactional(readOnly = true)
    public List<SessionView> sessions(UUID userId, UUID currentSessionId) {
        List<RefreshToken> live = refreshTokenRepository.findLiveByUser(new UserId(userId), clock.instant());
        Map<UUID, Instant> starts = refreshTokenRepository.sessionStarts(
                live.stream().map(RefreshToken::familyId).toList());
        return live.stream()
                .map(token -> new SessionView(token.familyId(), token.client().userAgent(),
                        token.client().ipAddress(), starts.getOrDefault(token.familyId(), token.issuedAt()),
                        token.issuedAt(), token.expiresAt(), token.familyId().equals(currentSessionId)))
                .toList();
    }

    /**
     * Ends one of the user's sessions.
     *
     * @throws SessionNotFoundException if the user has no live session with that id
     */
    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        boolean owned = refreshTokenRepository.findLiveByUser(new UserId(userId), now).stream()
                .anyMatch(token -> token.familyId().equals(sessionId));
        if (!owned) {
            throw new SessionNotFoundException();
        }
        refreshTokenRepository.revokeFamily(sessionId, now);
        events.publishEvent(new SessionsRevoked(userId, 1, now));
    }

    /**
     * Ends every session of the user except the one making the request.
     *
     * @return how many were ended
     */
    @Transactional
    public int revokeOthers(UUID userId, UUID currentSessionId) {
        Instant now = clock.instant();
        List<UUID> others = refreshTokenRepository.findLiveByUser(new UserId(userId), now).stream()
                .map(RefreshToken::familyId)
                .filter(familyId -> !Objects.equals(familyId, currentSessionId))
                .toList();
        others.forEach(familyId -> refreshTokenRepository.revokeFamily(familyId, now));
        if (!others.isEmpty()) {
            events.publishEvent(new SessionsRevoked(userId, others.size(), now));
        }
        return others.size();
    }
}
