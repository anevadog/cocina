package com.killa.cocina.modules.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bloqueo temporal tras intentos fallidos (NFR-SEG-01, D3 de F2). */
class AppUserLockoutTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private static final int MAX_ATTEMPTS = 5;

    private static final Duration LOCK = Duration.ofMinutes(15);

    @Test
    @DisplayName("Los cuatro primeros fallos no bloquean; el quinto bloquea 15 minutos")
    void elQuintoIntentoBloqueaLaCuenta() {
        AppUser user = user();

        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            user = user.registerFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
            assertThat(user.isLocked(NOW)).isFalse();
            assertThat(user.lockedUntil()).isNull();
        }

        user = user.registerFailedLogin(NOW, MAX_ATTEMPTS, LOCK);

        assertThat(user.failedAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.lockedUntil()).isEqualTo(NOW.plus(LOCK));
    }

    @Test
    @DisplayName("El bloqueo caduca solo: pasado el plazo la cuenta vuelve a admitir acceso")
    void elBloqueoCaducaSinIntervencion() {
        AppUser locked = user();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            locked = locked.registerFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
        }

        assertThat(locked.isLocked(NOW.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(locked.isLocked(NOW.plus(LOCK))).isFalse();
    }

    @Test
    @DisplayName("Al caducar el bloqueo se limpian contador y fecha antes de comprobar la contraseña")
    void alCaducarElBloqueoSeReiniciaElContador() {
        AppUser locked = user();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            locked = locked.registerFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
        }

        AppUser unlocked = locked.unlockIfExpired(NOW.plus(LOCK));

        assertThat(unlocked.failedAttempts()).isZero();
        assertThat(unlocked.lockedUntil()).isNull();
        assertThat(unlocked.isLocked(NOW.plus(LOCK))).isFalse();
    }

    @Test
    @DisplayName("Un acceso correcto reinicia el contador y deja la cuenta utilizable")
    void elAccesoCorrectoReiniciaElContador() {
        AppUser user = user().registerFailedLogin(NOW, MAX_ATTEMPTS, LOCK);

        AppUser authenticated = user.registerSuccessfulLogin(NOW.plusSeconds(30));

        assertThat(authenticated.failedAttempts()).isZero();
        assertThat(authenticated.lockedUntil()).isNull();
        assertThat(authenticated.updatedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(authenticated.passwordHash()).isEqualTo(user.passwordHash());
    }

    @Test
    @DisplayName("Un usuario inactivo no se considera bloqueado: la baja es otra regla")
    void laBajaNoEsUnBloqueo() {
        AppUser inactive = new AppUser(1L, 1L, "chef", "Chef", "hash", Role.COOK, false, 0, null, NOW, NOW);

        assertThat(inactive.isLocked(NOW)).isFalse();
        assertThat(inactive.active()).isFalse();
    }

    private static AppUser user() {
        return new AppUser(1L, 1L, "chef", "Chef de cocina", "hash-argon2", Role.COOK, true, 0, null,
                NOW, NOW);
    }
}
