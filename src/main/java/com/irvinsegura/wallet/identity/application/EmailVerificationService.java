package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.EmailVerificationRequested;
import com.irvinsegura.wallet.identity.EmailVerified;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyVerifiedException;
import com.irvinsegura.wallet.identity.domain.model.InvalidOneTimeTokenException;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms that users own their email address. The email itself is sent by the notifications module from
 * {@link EmailVerificationRequested}.
 */
@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final OneTimeTokenService tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EmailVerificationService(UserRepository userRepository, OneTimeTokenService tokens,
            ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Sends a new link; earlier ones stop working once it is sent.
     *
     * @throws EmailAlreadyVerifiedException if there is nothing left to verify
     */
    @Transactional
    public void resendVerification(UUID userId) {
        User user = userRepository.findById(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException();
        }
        events.publishEvent(new EmailVerificationRequested(userId, clock.instant()));
    }

    /**
     * @throws InvalidOneTimeTokenException if the link is invalid, expired or was already used
     */
    @Transactional
    public void verify(String rawToken) {
        UserId userId = tokens.consume(rawToken, TokenPurpose.VERIFY_EMAIL);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(InvalidOneTimeTokenException::new);
        Instant now = clock.instant();
        if (user.verifyEmail(now)) {
            userRepository.save(user);
            events.publishEvent(new EmailVerified(userId.value(), now));
        }
    }
}
