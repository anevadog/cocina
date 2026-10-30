package com.killa.cocina.modules.auth.domain;

/**
 * Casos de uso de autenticación (NFR-SEG-02).
 *
 * <p>La implementación transaccional vive en {@code infrastructure}.</p>
 */
public interface AuthService {

    /**
     * Autentica por usuario y contraseña.
     *
     * @throws com.killa.cocina.shared.error.ApiException con {@code INVALID_CREDENTIALS} o
     *         {@code ACCOUNT_LOCKED}
     */
    AuthSession login(long establishmentId, String username, String password);

    /**
     * Rota el refresh token y emite un par nuevo.
     *
     * @throws com.killa.cocina.shared.error.ApiException con {@code INVALID_REFRESH_TOKEN}
     */
    AuthSession refresh(String rawRefreshToken);

    /** Revoca el refresh token presentado. Idempotente. */
    void logout(String rawRefreshToken);
}
