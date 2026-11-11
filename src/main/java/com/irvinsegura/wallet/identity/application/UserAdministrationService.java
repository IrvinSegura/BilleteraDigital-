package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountClosureCheck;
import com.irvinsegura.wallet.identity.StaffAccountCreated;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.UserStatusChanged;
import com.irvinsegura.wallet.identity.UserRoleChanged;
import com.irvinsegura.wallet.identity.UserUnlocked;
import com.irvinsegura.wallet.identity.domain.model.AdminCannotBeDisabledException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.RoleChangeBlockedException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.model.UserStatus;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository.DailySignups;
import com.irvinsegura.wallet.identity.domain.port.UserRepository.UserCounts;
import com.irvinsegura.wallet.identity.domain.port.UserRepository.UserSlice;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support use cases. Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class UserAdministrationService {

    private static final int MIN_SUGGESTION_TEXT = 2;
    private static final int MAX_SUGGESTIONS = 6;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecondFactorVerifier secondFactorVerifier;
    private final List<AccountClosureCheck> closureChecks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    private final ZoneId usersZone;

    UserAdministrationService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            SecondFactorVerifier secondFactorVerifier, List<AccountClosureCheck> closureChecks,
            ApplicationEventPublisher events, Clock clock,
            @Value("${wallet.accounts.limit-zone}") ZoneId usersZone) {
        this.usersZone = usersZone;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.closureChecks = List.copyOf(closureChecks);
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates an administrator or support agent account. Nobody sees its password: {@link StaffAccountCreated} emails
     * its owner the account data, with a password generated at that moment. If that email is lost, they can still reset
     * it from the sign-in page.
     *
     * @param role {@code ADMIN} or {@code SUPPORT}
     * @throws EmailAlreadyRegisteredException if the address already has an account
     */
    @Transactional
    public AdminUserView createStaff(UUID adminId, String rawEmail, String fullName, String role, String reason) {
        Role staffRole = Role.valueOf(role);
        Email email = Email.of(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Instant now = clock.instant();
        User user = User.registerStaff(email, PasswordHasher.NO_PASSWORD, fullName, staffRole, now);
        userRepository.save(user);
        UUID userId = user.id().value();
        events.publishEvent(new UserRegistered(userId, staffRole.name(), now));
        events.publishEvent(new StaffAccountCreated(userId, staffRole.name(), adminId, reason, now));
        return view(user, now);
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

    /**
     * A preview of the users that match what is being typed. Too short a text matches too much to be useful, so it
     * finds nobody.
     */
    @Transactional(readOnly = true)
    public List<UserSuggestion> suggest(String text) {
        String typed = text.strip();
        if (typed.length() < MIN_SUGGESTION_TEXT) {
            return List.of();
        }
        return userRepository.findMatching(typed, MAX_SUGGESTIONS).stream()
                .map(user -> new UserSuggestion(user.id().value(), user.email().value(), user.fullName(),
                        user.status().name()))
                .toList();
    }

    /**
     * The accounts, the newest first, to browse them by state instead of searching for one.
     *
     * @param status {@code ACTIVE} or {@code DISABLED}; null for both
     * @param role {@code USER}, {@code SUPPORT} or {@code ADMIN}; null for both
     */
    @Transactional(readOnly = true)
    public AdminUserPage listUsers(String status, String role, int page, int size) {
        UserSlice slice = userRepository.findPage(status == null ? null : UserStatus.valueOf(status),
                role == null ? null : Role.valueOf(role), page, size);
        Instant now = clock.instant();
        return new AdminUserPage(slice.items().stream().map(user -> AdminUserSummary.from(user, now)).toList(),
                page, size, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public UserCounts countUsers() {
        return userRepository.count();
    }

    /**
     * Customers who signed up in each of the last {@code days} days, today included, by the days the users live in.
     */
    @Transactional(readOnly = true)
    public List<DailySignups> dailySignups(int days) {
        LocalDate first = LocalDate.now(clock.withZone(usersZone)).minusDays(days - 1L);
        return userRepository.countSignupsSince(first.atStartOfDay(usersZone).toInstant(), usersZone);
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
     * Makes an account a support agent or back into a regular user. Its sessions end, so the new role applies from its
     * next sign-in; an access token already issued can't reach cases that aren't assigned to it anyway.
     *
     * Staff can't move money, so a customer becomes an agent only once nothing of theirs is left in the app: the same
     * checks as closing the account, under the same lock.
     *
     * @throws com.irvinsegura.wallet.identity.domain.model.RoleChangeNotAllowedException for the administrator
     * @throws RoleChangeBlockedException if the customer still has money or operations in flight
     */
    @Transactional
    public AdminUserView changeRole(UUID userId, UUID adminId, String role, String reason) {
        User user = lock(userId);
        Instant now = clock.instant();
        if (Role.valueOf(role) == Role.SUPPORT && user.role() == Role.USER) {
            for (AccountClosureCheck check : closureChecks) {
                check.blocker(userId).ifPresent(blocker -> {
                    throw new RoleChangeBlockedException(blocker);
                });
            }
        }
        if (user.changeRole(Role.valueOf(role))) {
            userRepository.save(user);
            refreshTokenRepository.revokeAllForUser(user.id(), now);
            events.publishEvent(new UserRoleChanged(userId, role, adminId, reason, now));
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
