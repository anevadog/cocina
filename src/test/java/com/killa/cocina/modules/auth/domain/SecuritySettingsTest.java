package com.killa.cocina.modules.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parámetros de sesión: los límites de NFR-SEG-02 se validan al construir el bean. */
class SecuritySettingsTest {

    @Test
    @DisplayName("El token de acceso no puede superar los 15 minutos")
    void rechazaAccessTokenDemasiadoLargo() {
        assertThatThrownBy(() -> new SecuritySettings(Duration.ofMinutes(16), Duration.ofDays(30), 5,
                Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15 minutos");
    }

    @Test
    @DisplayName("El refresh debe durar más que el acceso y el bloqueo debe ser positivo")
    void rechazaCombinacionesIncoherentes() {
        assertThatThrownBy(() -> new SecuritySettings(Duration.ofMinutes(15), Duration.ofMinutes(15), 5,
                Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecuritySettings(Duration.ZERO, Duration.ofDays(30), 5,
                Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecuritySettings(Duration.ofMinutes(15), Duration.ofDays(30), 0,
                Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecuritySettings(Duration.ofMinutes(15), Duration.ofDays(30), 5,
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Una configuración razonable se acepta tal cual")
    void aceptaConfiguracionValida() {
        SecuritySettings settings = new SecuritySettings(Duration.ofMinutes(15), Duration.ofDays(30), 5,
                Duration.ofMinutes(15));

        assertThat(settings.accessTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(settings.maxFailedAttempts()).isEqualTo(5);
    }
}
