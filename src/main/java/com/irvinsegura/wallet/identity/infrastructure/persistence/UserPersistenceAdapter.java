package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserStatus;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
class UserPersistenceAdapter implements UserRepository {

    private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_users_email";

    private final UserJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(UserEntity::toDomain);
    }

    @Override
    public List<User> findAllById(Collection<UserId> ids) {
        return jpaRepository.findAllById(ids.stream().map(UserId::value).toList()).stream()
                .map(UserEntity::toDomain)
                .toList();
    }

    @Override
    public List<UUID> findActiveIdsByRole(Role role) {
        return jpaRepository.findIdsByRoleAndStatus(role, UserStatus.ACTIVE);
    }

    @Override
    public Optional<User> findByIdForUpdate(UserId id) {
        return findLocked(id, LockModeType.PESSIMISTIC_WRITE);
    }

    // PESSIMISTIC_READ is SELECT ... FOR SHARE in PostgreSQL
    @Override
    public Optional<User> findByIdForShare(UserId id) {
        return findLocked(id, LockModeType.PESSIMISTIC_READ);
    }

    private Optional<User> findLocked(UserId id, LockModeType lockMode) {
        UserEntity entity = entityManager.find(UserEntity.class, id.value());
        if (entity == null) {
            return Optional.empty();
        }
        // Refreshing under the lock reloads the row, even if an older copy was read earlier in this transaction
        entityManager.refresh(entity, lockMode);
        return Optional.of(entity.toDomain());
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value()).map(UserEntity::toDomain);
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
    public List<User> findMatching(String text, int limit) {
        // What was typed is matched literally: % and _ are not wildcards here
        String literal = text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jpaRepository.findMatching("%" + literal + "%", literal + "%", PageRequest.of(0, limit)).stream()
                .map(UserEntity::toDomain)
                .toList();
    }

    @Override
    public UserSlice findPage(UserStatus status, Role role, int page, int size) {
        Slice<UserEntity> slice = jpaRepository.findPage(status, role, PageRequest.of(page, size));
        return new UserSlice(slice.getContent().stream().map(UserEntity::toDomain).toList(), slice.hasNext());
    }

    @Override
    public UserCounts count() {
        UserJpaRepository.CountsRow row = jpaRepository.countByState();
        return new UserCounts(row.getTotal(), row.getActive(), row.getDisabled(), row.getUnverified(),
                row.getAdmins());
    }

    @Override
    public List<DailySignups> countSignupsSince(Instant since, ZoneId zone) {
        return jpaRepository.countSignupsSince(since, zone.getId()).stream()
                .map(row -> new DailySignups(LocalDate.parse(row.getDay()), row.getSignups()))
                .toList();
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
