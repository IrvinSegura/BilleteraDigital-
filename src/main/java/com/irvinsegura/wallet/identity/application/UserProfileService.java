package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.ProfileUpdated;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationConflictException;
import com.irvinsegura.wallet.identity.domain.model.InvalidProfileException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.IdentityVerificationRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final SecondFactorVerifier secondFactorVerifier;
    private final IdentityVerificationRepository identityVerifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserProfileService(UserRepository userRepository, SecondFactorVerifier secondFactorVerifier,
            IdentityVerificationRepository identityVerifications, ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.identityVerifications = identityVerifications;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return userRepository.findById(new UserId(userId))
                .map(user -> UserProfile.from(user, secondFactorVerifier.isEnabled(user.id())))
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * Changes the user's own name and phone; {@code null} keeps a field, a blank phone removes it. The email isn't
     * editable here: it is how the user signs in and receives money.
     *
     * @throws InvalidProfileException if the name or the phone is invalid
     * @throws IdentityVerificationConflictException {@code NAME_LOCKED} for a new name once an identity document is
     *         pending or verified, since the name must stay the one on it
     */
    @Transactional
    public UserProfile updateProfile(UUID userId, String fullName, String phone) {
        User user = userRepository.findById(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        List<String> changed = user.updateProfile(fullName, phone);
        if (changed.contains("FULL_NAME")
                && identityVerifications.findLatestByUser(userId).filter(IdentityVerification::isOpen).isPresent()) {
            throw new IdentityVerificationConflictException(IdentityVerificationConflictException.NAME_LOCKED);
        }
        if (!changed.isEmpty()) {
            userRepository.save(user);
            events.publishEvent(new ProfileUpdated(userId, changed, clock.instant()));
        }
        return UserProfile.from(user, secondFactorVerifier.isEnabled(user.id()));
    }

    /**
     * Sets the language of the user's emails, which the client app keeps in step with the one it shows. A preference
     * with no bearing on security, so unlike the profile it is not audited.
     */
    @Transactional
    public UserProfile changeLanguage(UUID userId, Language language) {
        User user = userRepository.findById(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        if (user.changeLanguage(language)) {
            userRepository.save(user);
        }
        return UserProfile.from(user, secondFactorVerifier.isEnabled(user.id()));
    }
}
