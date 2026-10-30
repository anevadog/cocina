package com.killa.cocina.modules.users.infrastructure;

import com.killa.cocina.modules.users.domain.AppUser;
import com.killa.cocina.modules.users.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code app_users}. */
@Entity
@Table(name = "app_users")
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_users_seq")
    @SequenceGenerator(name = "app_users_seq", sequenceName = "app_users_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUserEntity() {
    }

    private AppUserEntity(AppUser user) {
        apply(user);
        this.createdAt = user.createdAt();
    }

    static AppUserEntity from(AppUser user) {
        return new AppUserEntity(user);
    }

    /** Copia el estado del dominio sobre la entidad; no toca {@code createdAt}. */
    void apply(AppUser user) {
        this.id = user.id();
        this.establishmentId = user.establishmentId();
        this.username = user.username();
        this.fullName = user.fullName();
        this.passwordHash = user.passwordHash();
        this.role = user.role();
        this.active = user.active();
        this.failedAttempts = user.failedAttempts();
        this.lockedUntil = user.lockedUntil();
        this.updatedAt = user.updatedAt();
    }

    AppUser toDomain() {
        return new AppUser(id, establishmentId, username, fullName, passwordHash, role, active,
                failedAttempts, lockedUntil, createdAt, updatedAt);
    }

    Long getId() {
        return id;
    }
}
