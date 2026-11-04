package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.ProfileUpdated;
import com.irvinsegura.wallet.identity.domain.model.InvalidProfileException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
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
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserProfileService(UserRepository userRepository, SecondFactorVerifier secondFactorVerifier,
            ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.secondFactorVerifier = secondFactorVerifier;
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
     */
    @Transactional
    public UserProfile updateProfile(UUID userId, String fullName, String phone) {
        User user = userRepository.findById(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        List<String> changed = user.updateProfile(fullName, phone);
        if (!changed.isEmpty()) {
            userRepository.save(user);
            events.publishEvent(new ProfileUpdated(userId, changed, clock.instant()));
        }
        return UserProfile.from(user, secondFactorVerifier.isEnabled(user.id()));
    }
}
