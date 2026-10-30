package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expired refresh tokens can never be used again, so once a whole login family has expired it is deleted.
 */
@Service
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    RefreshTokenCleanupService(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /**
     * @return how many tokens were deleted
     */
    @Transactional
    public int deleteExpiredTokens() {
        return refreshTokenRepository.deleteExpiredFamilies(clock.instant());
    }
}
