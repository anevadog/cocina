package com.killa.cocina.modules.auth.domain;

import java.time.Duration;

/**
 * Parámetros de seguridad de las sesiones, resueltos desde configuración.
 *
 * @param accessTtl vida del token de acceso (NFR-SEG-02: como máximo 15 minutos)
 * @param refreshTtl vida del refresh token
 * @param maxFailedAttempts intentos fallidos antes de bloquear la cuenta
 * @param lockDuration duración del bloqueo temporal
 */
public record SecuritySettings(Duration accessTtl, Duration refreshTtl, int maxFailedAttempts,
        Duration lockDuration) {

    /** Límite duro de NFR-SEG-02: el token de acceso no puede vivir más de 15 minutos. */
    public static final Duration MAX_ACCESS_TTL = Duration.ofMinutes(15);

    public SecuritySettings {
        if (accessTtl == null || accessTtl.isNegative() || accessTtl.isZero()) {
            throw new IllegalArgumentException("La vida del token de acceso debe ser positiva.");
        }
        if (accessTtl.compareTo(MAX_ACCESS_TTL) > 0) {
            throw new IllegalArgumentException(
                    "La vida del token de acceso no puede superar 15 minutos (NFR-SEG-02).");
        }
        if (refreshTtl == null || refreshTtl.compareTo(accessTtl) <= 0) {
            throw new IllegalArgumentException(
                    "El refresh token debe durar más que el token de acceso.");
        }
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("Debe permitirse al menos un intento fallido.");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("La duración del bloqueo debe ser positiva.");
        }
    }
}
