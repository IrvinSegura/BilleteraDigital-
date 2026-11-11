package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountClosed;
import com.irvinsegura.wallet.identity.AccountClosureCheck;
import com.irvinsegura.wallet.identity.EmailChangeRequested;
import com.irvinsegura.wallet.identity.EmailChanged;
import com.irvinsegura.wallet.identity.PersonalDataSource;
import com.irvinsegura.wallet.identity.domain.model.AccountCannotBeClosedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.InvalidOneTimeTokenException;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.OneTimeTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What owners do with the account itself: move it to another address, close it, or take a copy of their data.
 * Changing the address and closing ask for the password, and for the second factor of users who have one, in a
 * transaction of their own, so a wrong one counts toward the lockout even though the request fails.
 */
@Service
public class AccountLifecycleService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final OneTimeTokenService tokens;
    private final UserDirectoryService credentials;
    private final UserProfileService profiles;
    private final SessionService sessions;
    private final List<AccountClosureCheck> closureChecks;
    private final List<PersonalDataSource> personalData;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    AccountLifecycleService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            OneTimeTokenRepository oneTimeTokenRepository, OneTimeTokenService tokens,
            UserDirectoryService credentials, UserProfileService profiles, SessionService sessions,
            List<AccountClosureCheck> closureChecks, List<PersonalDataSource> personalData,
            ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.tokens = tokens;
        this.credentials = credentials;
        this.profiles = profiles;
        this.sessions = sessions;
        this.closureChecks = List.copyOf(closureChecks);
        this.personalData = personalData.stream().sorted(Comparator.comparing(PersonalDataSource::section)).toList();
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Emails a link to the new address; the account keeps the current one until the link is followed. An address
     * that already has an account gets nothing, so the answer never reveals who is registered.
     *
     * @param language the language of the app asking; null to use the account's
     * @throws EmailAlreadyRegisteredException if it is the account's own address
     */
    public void requestEmailChange(UUID userId, String newEmail, String currentPassword, String twoFactorCode,
            Language language) {
        Email address = Email.of(newEmail);
        credentials.confirmPasswordAndSecondFactor(userId, currentPassword, twoFactorCode);
        transaction.executeWithoutResult(status -> {
            User user = userRepository.findById(new UserId(userId)).orElseThrow(UserNotFoundException::new);
            if (user.email().equals(address)) {
                throw new EmailAlreadyRegisteredException();
            }
            events.publishEvent(new EmailChangeRequested(userId, address.value(), language, clock.instant()));
        });
    }

    /**
     * Moves the account to the address the link was sent to and ends every session. Links still pending for the
     * old address stop working: whoever reads that inbox may be why the address changed.
     *
     * @throws InvalidOneTimeTokenException    if the link is invalid, expired or was already used
     * @throws EmailAlreadyRegisteredException if the address got an account in the meantime
     */
    @Transactional
    public void confirmEmailChange(String rawToken) {
        OneTimeTokenService.EmailChange change = tokens.consumeEmailChange(rawToken);
        User user = userRepository.findByIdForUpdate(change.userId())
                .filter(User::isActive)
                .orElseThrow(InvalidOneTimeTokenException::new);
        Instant now = clock.instant();
        Email previous = user.changeEmail(change.newEmail(), now);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.id(), now);
        oneTimeTokenRepository.deleteByUser(user.id(), TokenPurpose.RESET_PASSWORD);
        oneTimeTokenRepository.deleteByUser(user.id(), TokenPurpose.VERIFY_EMAIL);
        events.publishEvent(new EmailChanged(user.id().value(), previous.value(), change.newEmail().value(), now));
    }

    /**
     * Closes the account for good once every module says nothing of the user's is left in it. The user's row stays
     * locked from the checks to the close, and money only leaves under a shared lock on it.
     *
     * @throws AccountCannotBeClosedException with the first reason found
     */
    public void close(UUID userId, String currentPassword, String twoFactorCode) {
        credentials.confirmPasswordAndSecondFactor(userId, currentPassword, twoFactorCode);
        transaction.executeWithoutResult(status -> {
            User user = userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
            for (AccountClosureCheck check : closureChecks) {
                check.blocker(userId).ifPresent(reason -> {
                    throw new AccountCannotBeClosedException(reason);
                });
            }
            user.close();
            userRepository.save(user);
            Instant now = clock.instant();
            refreshTokenRepository.revokeAllForUser(user.id(), now);
            events.publishEvent(new AccountClosed(userId, now));
        });
    }

    /**
     * Everything kept about the user, by section, for them to download.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", clock.instant());
        data.put("profile", profiles.getProfile(userId));
        data.put("sessions", sessions.sessions(userId, null));
        for (PersonalDataSource source : personalData) {
            data.put(source.section(), source.collect(userId));
        }
        return data;
    }
}
