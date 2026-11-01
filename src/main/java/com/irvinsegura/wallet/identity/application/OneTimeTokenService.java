package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountTokens;
import com.irvinsegura.wallet.identity.IssuedToken;
import com.irvinsegura.wallet.identity.domain.model.InvalidOneTimeTokenException;
import com.irvinsegura.wallet.identity.domain.model.OneTimeToken;
import com.irvinsegura.wallet.identity.domain.model.OneTimeTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.OneTimeTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the tokens of email links and checks them when they come back.
 */
@Service
public class OneTimeTokenService implements AccountTokens {

    private final OneTimeTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final OneTimeTokenPolicy policy;
    private final Clock clock;

    OneTimeTokenService(OneTimeTokenRepository tokenRepository, UserRepository userRepository,
            OneTimeTokenPolicy policy, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<IssuedToken> issueEmailVerification(UUID userId) {
        return issue(new UserId(userId), TokenPurpose.VERIFY_EMAIL, user -> !user.isEmailVerified());
    }

    @Override
    @Transactional
    public Optional<IssuedToken> issuePasswordReset(UUID userId) {
        return issue(new UserId(userId), TokenPurpose.RESET_PASSWORD, User::isActive);
    }

    /**
     * Uses up the token: it is deleted with any other of the user's tokens for that purpose, in the caller's
     * transaction, so a rollback makes it usable again.
     *
     * @return the user the token was issued to
     * @throws InvalidOneTimeTokenException if the token is unknown, expired, already used or for another purpose
     */
    @Transactional
    public UserId consume(String rawToken, TokenPurpose purpose) {
        OneTimeToken token = tokenRepository.findByTokenHashForUpdate(OneTimeToken.hash(rawToken))
                .filter(found -> found.isValidFor(purpose, clock.instant()))
                .orElseThrow(InvalidOneTimeTokenException::new);
        tokenRepository.deleteByUser(token.userId(), purpose);
        return token.userId();
    }

    private Optional<IssuedToken> issue(UserId userId, TokenPurpose purpose, Predicate<User> eligible) {
        if (userRepository.findById(userId).filter(eligible).isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        // Only the newest link works, so an older email lying around in an inbox is useless
        tokenRepository.deleteByUser(userId, purpose);
        OneTimeToken.Issued issued = OneTimeToken.issue(userId, purpose, now, policy.timeToLive(purpose));
        tokenRepository.save(issued.token());
        return Optional.of(new IssuedToken(issued.rawValue(), issued.token().expiresAt()));
    }
}
