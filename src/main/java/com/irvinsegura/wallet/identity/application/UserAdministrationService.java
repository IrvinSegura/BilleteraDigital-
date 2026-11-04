package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.UserStatusChanged;
import com.irvinsegura.wallet.identity.UserUnlocked;
import com.irvinsegura.wallet.identity.domain.model.AdminCannotBeDisabledException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support use cases. Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecondFactorVerifier secondFactorVerifier;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserAdministrationService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            SecondFactorVerifier secondFactorVerifier, ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminUserView findByEmail(String email) {
        try {
            return userRepository.findByEmail(Email.of(email))
                    .map(user -> view(user, clock.instant()))
                    .orElseThrow(UserNotFoundException::new);
        } catch (InvalidEmailException e) {
            throw new UserNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public AdminUserView getUser(UUID userId) {
        return userRepository.findById(new UserId(userId))
                .map(user -> view(user, clock.instant()))
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * Lifts a lockout, e.g. when someone locked the account on purpose. Unlocking an account that is not locked
     * changes nothing and is not audited.
     */
    @Transactional
    public AdminUserView unlock(UUID userId, UUID adminId, String reason) {
        User user = lock(userId);
        Instant now = clock.instant();
        boolean wasLocked = user.unlock(now);
        userRepository.save(user);
        if (wasLocked) {
            events.publishEvent(new UserUnlocked(userId, adminId, reason, now));
        }
        return view(user, now);
    }

    /**
     * Blocks login and every money movement, and ends the account's sessions. Access tokens already issued keep
     * reading data until they expire, but cannot move money: those operations check the status under a lock.
     *
     * @throws AdminCannotBeDisabledException for the administrator account
     */
    @Transactional
    public AdminUserView disable(UUID userId, UUID adminId, String reason) {
        // Exclusive lock: waits for money movements in flight and blocks the ones after it
        User user = lock(userId);
        Instant now = clock.instant();
        if (user.disable()) {
            userRepository.save(user);
            refreshTokenRepository.revokeAllForUser(user.id(), now);
            events.publishEvent(new UserStatusChanged(userId, user.status().name(), adminId, reason, now));
        }
        return view(user, now);
    }

    @Transactional
    public AdminUserView enable(UUID userId, UUID adminId, String reason) {
        User user = lock(userId);
        Instant now = clock.instant();
        if (user.enable()) {
            userRepository.save(user);
            events.publishEvent(new UserStatusChanged(userId, user.status().name(), adminId, reason, now));
        }
        return view(user, now);
    }

    // Support needs it before resetting a lost authenticator app
    private AdminUserView view(User user, Instant now) {
        return AdminUserView.from(user, secondFactorVerifier.isEnabled(user.id()), now);
    }

    private User lock(UUID userId) {
        return userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
    }
}
