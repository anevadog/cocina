package com.killa.cocina.modules.auth.domain;

import java.time.Instant;
import java.util.Optional;

/** Puerto de persistencia de refresh tokens. */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    /** Revoca todos los tokens vivos de un usuario (detección de reutilización). */
    int revokeAll(long userId, Instant now);
}
