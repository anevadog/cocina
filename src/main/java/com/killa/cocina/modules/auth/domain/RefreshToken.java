package com.killa.cocina.modules.auth.domain;

import java.time.Instant;

/**
 * Refresh token almacenado (NFR-SEG-02). El valor en claro nunca se persiste.
 *
 * <p>{@code revokedAt} marca un token ya usado o anulado y {@code replacedBy} enlaza con el token
 * que lo sustituyó, de modo que se puede detectar la reutilización de un token rotado.</p>
 */
public record RefreshToken(Long id, long establishmentId, long userId, String tokenHash,
        Instant expiresAt, Instant revokedAt, Long replacedBy, Instant createdAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public RefreshToken revoke(Instant now, Long replacementId) {
        return new RefreshToken(id, establishmentId, userId, tokenHash, expiresAt, now,
                replacementId, createdAt);
    }
}
