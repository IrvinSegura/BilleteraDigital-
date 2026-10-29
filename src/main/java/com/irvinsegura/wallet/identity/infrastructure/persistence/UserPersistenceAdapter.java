package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class UserPersistenceAdapter implements UserRepository {

    private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_users_email";

    private final UserJpaRepository jpaRepository;

    UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmailForUpdate(Email email) {
        return jpaRepository.findByEmailForUpdate(email.value()).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    @Override
    public void save(User user) {
        try {
            // Flush now so a concurrent registration with the same email fails here, not at commit time
            jpaRepository.saveAndFlush(UserEntity.fromDomain(user));
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && UNIQUE_EMAIL_CONSTRAINT.equals(violation.getConstraintName())) {
                throw new EmailAlreadyRegisteredException();
            }
            throw ex;
        }
    }
}
