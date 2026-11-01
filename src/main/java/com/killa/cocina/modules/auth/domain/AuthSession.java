package com.killa.cocina.modules.auth.domain;

import java.time.Instant;

/**
 * Par de tokens emitido tras autenticarse (NFR-SEG-02).
 *
 * @param accessToken token de acceso firmado (JWT)
 * @param accessExpiresAt caducidad del token de acceso
 * @param refreshToken refresh token opaco; solo se guarda su hash
 * @param refreshExpiresAt caducidad del refresh token
 */
public record AuthSession(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {}
