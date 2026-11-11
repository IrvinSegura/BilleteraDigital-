package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.email = :email")
    Optional<UserEntity> findByEmailForUpdate(@Param("email") String email);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    // Scans the table, which is fine for a support search run by a handful of administrators
    @Query("""
            select u from UserEntity u
            where lower(u.email) like :contains escape '\\'
               or lower(u.fullName) like :contains escape '\\'
               or cast(u.id as string) like :startsWith escape '\\'
            order by case when lower(u.email) like :startsWith escape '\\' then 0 else 1 end, u.email""")
    List<UserEntity> findMatching(@Param("contains") String contains, @Param("startsWith") String startsWith,
            Pageable page);

    @Query("select u.id from UserEntity u where u.role = :role and u.status = :status")
    List<UUID> findIdsByRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus status);

    @Query("""
            select u from UserEntity u
            where (:status is null or u.status = :status) and (:role is null or u.role = :role)
            order by u.createdAt desc, u.id""")
    Slice<UserEntity> findPage(@Param("status") UserStatus status, @Param("role") Role role, Pageable page);

    @Query(value = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE status = 'ACTIVE') AS active,
                   count(*) FILTER (WHERE status = 'DISABLED') AS disabled,
                   count(*) FILTER (WHERE email_verified_at IS NULL) AS unverified,
                   count(*) FILTER (WHERE role = 'ADMIN') AS admins
            FROM users""", nativeQuery = true)
    CountsRow countByState();

    @Query(value = """
            SELECT to_char(created_at AT TIME ZONE CAST(:zone AS text), 'YYYY-MM-DD') AS day, count(*) AS signups
            FROM users
            WHERE role = 'USER' AND created_at >= :since
            GROUP BY day
            ORDER BY day""", nativeQuery = true)
    List<SignupsRow> countSignupsSince(@Param("since") Instant since, @Param("zone") String zone);

    interface SignupsRow {

        String getDay();

        long getSignups();
    }

    interface CountsRow {

        long getTotal();

        long getActive();

        long getDisabled();

        long getUnverified();

        long getAdmins();
    }

}
