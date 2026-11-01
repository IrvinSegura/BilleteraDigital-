package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.port.OneTimeTokenRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Used tokens are deleted right away; expired ones that nobody used are deleted here.
 */
@Service
public class OneTimeTokenCleanupService {

    private final OneTimeTokenRepository tokenRepository;
    private final Clock clock;

    OneTimeTokenCleanupService(OneTimeTokenRepository tokenRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    /**
     * @return how many tokens were deleted
     */
    @Transactional
    public int deleteExpiredTokens() {
        return tokenRepository.deleteExpired(clock.instant());
    }
}
