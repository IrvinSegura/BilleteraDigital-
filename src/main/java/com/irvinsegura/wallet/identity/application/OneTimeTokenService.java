package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountTokens;
import com.irvinsegura.wallet.identity.IssuedToken;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.InvalidOneTimeTokenException;
import com.irvinsegura.wallet.identity.domain.model.OneTimeToken;
import com.irvinsegura.wallet.identity.domain.model.OneTimeTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.OneTimeTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the tokens of email links and checks them when they come back, and the first password of a staff account,
 * which also travels by email.
 */
@Service
public class OneTimeTokenService implements AccountTokens {

    private final OneTimeTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final OneTimeTokenPolicy policy;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    OneTimeTokenService(OneTimeTokenRepository tokenRepository, UserRepository userRepository,
            OneTimeTokenPolicy policy, PasswordHasher passwordHasher, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.passwordHasher = passwordHasher;
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

    @Override
    @Transactional
    public Optional<IssuedToken> issueEmailChange(UUID userId, String newEmail) {
        Email address = Email.of(newEmail);
        UserId id = new UserId(userId);
        if (userRepository.findById(id).filter(User::isActive).isEmpty()
                || userRepository.findByEmail(address).isPresent()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        tokenRepository.deleteByUser(id, TokenPurpose.CHANGE_EMAIL);
        OneTimeToken.Issued issued = OneTimeToken.issueEmailChange(id, address, now,
                policy.timeToLive(TokenPurpose.CHANGE_EMAIL));
        tokenRepository.save(issued.token());
        return Optional.of(new IssuedToken(issued.rawValue(), issued.token().expiresAt()));
    }

    @Override
    @Transactional
    public Optional<String> issueInitialPassword(UUID userId) {
        return userRepository.findById(new UserId(userId))
                .filter(User::isActive)
                .filter(user -> PasswordHasher.NO_PASSWORD.equals(user.passwordHash()))
                .map(user -> {
                    String password = InitialPasswords.generate(user.email().value(), user.fullName());
                    user.giveInitialPassword(passwordHasher.hash(password));
                    userRepository.save(user);
                    return password;
                });
    }

    /**
     * Uses up an email change link, like {@link #consume}.
     *
     * @return the user and the address the link confirms
     * @throws InvalidOneTimeTokenException if the link is unknown, expired or already used
     */
    @Transactional
    public EmailChange consumeEmailChange(String rawToken) {
        OneTimeToken token = find(rawToken, TokenPurpose.CHANGE_EMAIL);
        tokenRepository.deleteByUser(token.userId(), TokenPurpose.CHANGE_EMAIL);
        return new EmailChange(token.userId(), token.newEmail());
    }

    public record EmailChange(UserId userId, Email newEmail) {
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
        OneTimeToken token = find(rawToken, purpose);
        tokenRepository.deleteByUser(token.userId(), purpose);
        return token.userId();
    }

    private OneTimeToken find(String rawToken, TokenPurpose purpose) {
        return tokenRepository.findByTokenHashForUpdate(OneTimeToken.hash(rawToken))
                .filter(found -> found.isValidFor(purpose, clock.instant()))
                .orElseThrow(InvalidOneTimeTokenException::new);
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
