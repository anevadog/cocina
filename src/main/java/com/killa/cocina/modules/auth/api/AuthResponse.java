package com.killa.cocina.modules.auth.api;

import com.killa.cocina.modules.auth.domain.AuthSession;
import java.time.Instant;

/**
 * Respuesta de login y de refresh.
 *
 * @param tokenType siempre {@code Bearer}; se envía en {@code Authorization}
 */
public record AuthResponse(
        String accessToken, String tokenType, Instant expiresAt, String refreshToken, Instant refreshExpiresAt) {

    public static AuthResponse from(AuthSession session) {
        return new AuthResponse(
                session.accessToken(),
                "Bearer",
                session.accessExpiresAt(),
                session.refreshToken(),
                session.refreshExpiresAt());
    }
}
