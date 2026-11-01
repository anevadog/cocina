package com.killa.cocina.modules.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash de refresh tokens (NFR-SEG-02).
 *
 * <p>El token es aleatorio de 256 bits, así que un SHA-256 es suficiente: no hace falta una función
 * lenta como Argon2, que está pensada para secretos de baja entropía.</p>
 */
public final class TokenHash {

    private TokenHash() {}

    public static String of(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en la JVM.", exception);
        }
    }
}
