package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected UserEntity() {
    }

    static UserEntity fromDomain(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id().value();
        entity.email = user.email().value();
        entity.passwordHash = user.passwordHash();
        entity.fullName = user.fullName();
        entity.role = user.role();
        entity.status = user.status();
        entity.emailVerifiedAt = user.emailVerifiedAt();
        entity.failedLoginAttempts = user.failedLoginAttempts();
        entity.lockedUntil = user.lockedUntil();
        entity.createdAt = user.createdAt();
        entity.version = user.version();
        return entity;
    }

    User toDomain() {
        return User.restore(new UserId(id), new Email(email), passwordHash, fullName, role, status, emailVerifiedAt,
                failedLoginAttempts, lockedUntil, createdAt, version);
    }
}
