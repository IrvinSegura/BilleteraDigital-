package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UserId id);

    List<User> findAllById(Collection<UserId> ids);

    List<UUID> findActiveIdsByRole(Role role);

    /**
     * Loads the user with an exclusive row lock, taken by changes to the account (password, status, lockout).
     */
    Optional<User> findByIdForUpdate(UserId id);

    /**
     * Loads the user with a shared row lock, taken by operations that move money. Several can hold it at once, but
     * disabling the account waits for them and they wait for a disable in progress.
     */
    Optional<User> findByIdForShare(UserId id);

    Optional<User> findByEmail(Email email);

    /**
     * Loads the user with a row lock, so concurrent logins cannot lose failed-attempt increments.
     */
    Optional<User> findByEmailForUpdate(Email email);

    boolean existsByEmail(Email email);

    /**
     * The users whose email or name contains the text, or whose id starts with it, ignoring case: those whose email
     * starts with it first, then by email.
     */
    List<User> findMatching(String text, int limit);

    /**
     * A page of users, the newest first, only those with the status and role when given.
     */
    UserSlice findPage(UserStatus status, Role role, int page, int size);

    /**
     * How many accounts there are, in total and by state, for the administration overview.
     */
    UserCounts count();

    /**
     * Customers who signed up at or after {@code since}, by day in {@code zone}, the oldest first. Days with nobody
     * have no row.
     */
    List<DailySignups> countSignupsSince(Instant since, ZoneId zone);

    /**
     * Inserts or updates the user. Throws {@code EmailAlreadyRegisteredException} when the email is already taken.
     */
    void save(User user);

    record UserSlice(List<User> items, boolean hasNext) {
    }

    /**
     * @param unverified accounts that never confirmed their email
     */
    record UserCounts(long total, long active, long disabled, long unverified, long admins) {
    }

    record DailySignups(LocalDate date, long count) {
    }

}
