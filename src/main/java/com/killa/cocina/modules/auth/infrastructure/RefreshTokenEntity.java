package com.killa.cocina.modules.auth.infrastructure;

import com.killa.cocina.modules.auth.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code refresh_tokens}. Solo se guarda el hash del token. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_tokens_seq")
    @SequenceGenerator(name = "refresh_tokens_seq", sequenceName = "refresh_tokens_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {}

    private RefreshTokenEntity(RefreshToken token) {
        apply(token);
    }

    static RefreshTokenEntity from(RefreshToken token) {
        return new RefreshTokenEntity(token);
    }

    void apply(RefreshToken token) {
        this.id = token.id();
        this.establishmentId = token.establishmentId();
        this.userId = token.userId();
        this.tokenHash = token.tokenHash();
        this.expiresAt = token.expiresAt();
        this.revokedAt = token.revokedAt();
        this.replacedBy = token.replacedBy();
        this.createdAt = token.createdAt();
    }

    RefreshToken toDomain() {
        return new RefreshToken(id, establishmentId, userId, tokenHash, expiresAt, revokedAt, replacedBy, createdAt);
    }
}
