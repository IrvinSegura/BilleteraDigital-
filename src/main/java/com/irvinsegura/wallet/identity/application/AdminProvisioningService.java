package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.PasswordPolicy;
import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the administrator account. An existing regular user is never promoted, even with a verified email: granting
 * the role to an account someone signed up for must be a deliberate step, not a side effect of configuration.
 */
@Service
public class AdminProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioningService.class);
    private static final String ADMIN_NAME = "Administrator";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AdminProvisioningService(UserRepository userRepository, PasswordHasher passwordHasher,
            ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @throws EmailAlreadyRegisteredException if another instance created the admin at the same moment
     */
    @Transactional
    public void ensureAdmin(String rawEmail, String rawPassword) {
        Email email = Email.of(rawEmail);
        Optional<User> existing = userRepository.findByEmailForUpdate(email);
        if (existing.isPresent()) {
            if (existing.get().role() != Role.ADMIN) {
                log.error("Admin email {} belongs to a regular user; it was not promoted", email);
            }
            return;
        }
        PasswordPolicy.check(rawPassword, rawEmail);
        User admin = User.registerAdmin(email, passwordHasher.hash(rawPassword), ADMIN_NAME, clock.instant());
        userRepository.save(admin);
        events.publishEvent(new UserRegistered(admin.id().value(), admin.role().name(), admin.createdAt()));
        log.info("Administrator account {} created", email);
    }
}
