package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailNotVerifiedException;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserReference;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.util.Optional;
import java.util.UUID;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<UserContact> findContact(UUID userId) {
        return userRepository.findById(new UserId(userId))
                .map(user -> new UserContact(user.id().value(), user.email().value(), user.fullName()));
    }

    @Override
    @Transactional
    public void authorizeOutgoingMoney(UUID userId) {
        if (!lockForMoneyMovement(userId).isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
    }

    @Override
    @Transactional
    public void authorizeDeposit(UUID userId) {
        lockForMoneyMovement(userId);
    }

    @Override
    @Transactional
    public void authorizeAccountChange(UUID userId) {
        lockForMoneyMovement(userId);
    }

    private User lockForMoneyMovement(UUID userId) {
        // Users are never deleted, so a missing one can only be a token for an account that never existed here
        User user = userRepository.findByIdForShare(new UserId(userId)).orElseThrow(AccountDisabledException::new);
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        return user;
    }
}
