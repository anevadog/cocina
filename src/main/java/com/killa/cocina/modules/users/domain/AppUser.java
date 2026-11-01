package com.killa.cocina.modules.users.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Usuario del establecimiento y reglas de bloqueo por intentos fallidos (PER-01, NFR-SEG-01).
 *
 * <p>Inmutable: cada cambio devuelve una instancia nueva. No conoce Spring ni persistencia, así que
 * las reglas de bloqueo se prueban sin contexto.</p>
 */
public record AppUser(
        Long id,
        long establishmentId,
        String username,
        String fullName,
        String passwordHash,
        Role role,
        boolean active,
        int failedAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Suma un intento fallido y bloquea la cuenta al alcanzar el máximo.
     *
     * @param now instante actual en UTC
     * @param maxFailedAttempts intentos permitidos antes del bloqueo
     * @param lockDuration duración del bloqueo temporal
     */
    public AppUser registerFailedLogin(Instant now, int maxFailedAttempts, Duration lockDuration) {
        int attempts = failedAttempts + 1;
        Instant lock = attempts >= maxFailedAttempts ? now.plus(lockDuration) : null;
        return new AppUser(
                id, establishmentId, username, fullName, passwordHash, role, active, attempts, lock, createdAt, now);
    }

    /** Reinicia el contador tras un acceso correcto. */
    public AppUser registerSuccessfulLogin(Instant now) {
        return new AppUser(
                id, establishmentId, username, fullName, passwordHash, role, active, 0, null, createdAt, now);
    }

    /** Si el bloqueo ya expiró, deja la cuenta limpia antes de comprobar la contraseña. */
    public AppUser unlockIfExpired(Instant now) {
        if (lockedUntil != null && !isLocked(now)) {
            return registerSuccessfulLogin(now);
        }
        return this;
    }

    /** Cambia los datos editables por un administrador (el username no se modifica). */
    public AppUser update(String fullName, Role role, boolean active, Instant now) {
        return new AppUser(
                id,
                establishmentId,
                username,
                fullName,
                passwordHash,
                role,
                active,
                failedAttempts,
                lockedUntil,
                createdAt,
                now);
    }
}
