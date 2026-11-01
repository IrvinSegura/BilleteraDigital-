package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.EmailVerificationRequested;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.PasswordPolicy;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegistrationService(UserRepository userRepository, PasswordHasher passwordHasher,
            ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UserProfile register(RegisterUserCommand command) {
        Email email = Email.of(command.email());
        PasswordPolicy.check(command.password());
        // Fast path for the common case; the unique constraint still catches concurrent registrations
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.register(email, passwordHasher.hash(command.password()), command.fullName(),
                clock.instant());
        userRepository.save(user);
        events.publishEvent(new UserRegistered(user.id().value(), user.role().name(), user.createdAt()));
        events.publishEvent(new EmailVerificationRequested(user.id().value(), user.createdAt()));
        return UserProfile.from(user);
    }
}
