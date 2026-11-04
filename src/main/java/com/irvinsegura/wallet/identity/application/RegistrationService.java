package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.EmailVerificationRequested;
import com.irvinsegura.wallet.identity.ExistingAccountSignUpAttempted;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.domain.model.BreachedPasswordException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.PasswordPolicy;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.BreachedPasswords;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final BreachedPasswords breachedPasswords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    RegistrationService(UserRepository userRepository, PasswordHasher passwordHasher,
            BreachedPasswords breachedPasswords, ApplicationEventPublisher events, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.breachedPasswords = breachedPasswords;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates the account and emails the link that verifies it, which it needs before it can sign in. When the address
     * already has an account nothing is created and its owner gets an email instead: a new link if it is still
     * unverified, a notice otherwise. Callers answer the same either way, so sign-up cannot tell anyone who is
     * registered.
     *
     * @throws BreachedPasswordException if the password appears in a known data breach
     */
    public void register(RegisterUserCommand command) {
        Email email = Email.of(command.email());
        PasswordPolicy.check(command.password(), command.email(), command.fullName());
        // A network call, so before the transaction: no database connection is held while it waits
        if (breachedPasswords.isBreached(command.password())) {
            throw new BreachedPasswordException();
        }
        // Hashed even when the address is taken, so both answers take the same time
        String passwordHash = passwordHasher.hash(command.password());
        try {
            transaction.executeWithoutResult(status -> create(email, passwordHash, command.fullName()));
        } catch (EmailAlreadyRegisteredException e) {
            // A concurrent sign-up with the same address won the unique constraint
            transaction.executeWithoutResult(status -> userRepository.findByEmail(email).ifPresent(this::tellOwner));
        }
    }

    private void create(Email email, String passwordHash, String fullName) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            tellOwner(existing.get());
            return;
        }
        User user = User.register(email, passwordHash, fullName, clock.instant());
        userRepository.save(user);
        events.publishEvent(new UserRegistered(user.id().value(), user.role().name(), user.createdAt()));
        events.publishEvent(new EmailVerificationRequested(user.id().value(), user.createdAt()));
    }

    // A disabled account gets nothing: there is nothing its owner could do about it
    private void tellOwner(User user) {
        Instant now = clock.instant();
        if (!user.isActive()) {
            return;
        }
        events.publishEvent(user.isEmailVerified()
                ? new ExistingAccountSignUpAttempted(user.id().value(), now)
                : new EmailVerificationRequested(user.id().value(), now));
    }
}
