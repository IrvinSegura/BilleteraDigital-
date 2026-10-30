package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserReference;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserReference> findByEmail(String email) {
        Email parsed;
        try {
            parsed = Email.of(email);
        } catch (InvalidEmailException e) {
            return Optional.empty();
        }
        return userRepository.findByEmail(parsed)
                .map(user -> new UserReference(user.id().value(), user.email().value()));
    }
}
