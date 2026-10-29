package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    /**
     * Loads the user with a row lock, so concurrent logins cannot lose failed-attempt increments.
     */
    Optional<User> findByEmailForUpdate(Email email);

    boolean existsByEmail(Email email);

    /**
     * Inserts or updates the user. Throws {@code EmailAlreadyRegisteredException} when the email is already taken.
     */
    void save(User user);
}
