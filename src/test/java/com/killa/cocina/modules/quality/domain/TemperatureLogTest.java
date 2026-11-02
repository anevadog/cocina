package com.killa.cocina.modules.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de una lectura de temperatura (CAL-02). */
class TemperatureLogTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    @DisplayName("El rango incluye sus extremos y deja fuera lo que se sale")
    void elRangoIncluyeSusExtremos() {
        assertThat(TemperatureLog.isWithinRange(new BigDecimal("2.00"), new BigDecimal("2.00"), new BigDecimal("8.00")))
                .isTrue();
        assertThat(TemperatureLog.isWithinRange(new BigDecimal("8.00"), new BigDecimal("2.00"), new BigDecimal("8.00")))
                .isTrue();
        assertThat(TemperatureLog.isWithinRange(new BigDecimal("8.01"), new BigDecimal("2.00"), new BigDecimal("8.00")))
                .isFalse();
        assertThat(TemperatureLog.isWithinRange(new BigDecimal("1.99"), new BigDecimal("2.00"), new BigDecimal("8.00")))
                .isFalse();
    }

    @Test
    @DisplayName("Una lectura fuera de rango es válida: el rango no la rechaza, la marca")
    void unaLecturaFueraDeRangoSigueSiendoValida() {
        TemperatureLog log = new TemperatureLog(
                null,
                1L,
                TemperatureKind.EQUIPMENT,
                "Cámara 1",
                new BigDecimal("12.5"),
                new BigDecimal("2"),
                new BigDecimal("8"),
                false,
                7L,
                NOW,
                NOW);

        assertThat(log.withinRange()).isFalse();
        assertThat(log.valueC()).isEqualByComparingTo("12.50");
        assertThat(log.minC()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("El rango debe ser creciente y las temperaturas admiten dos decimales")
    void validaRangoYDecimales() {
        assertThatThrownBy(() -> registration(new BigDecimal("5"), new BigDecimal("8"), new BigDecimal("8")))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> registration(new BigDecimal("5.555"), new BigDecimal("2"), new BigDecimal("8")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("La petición contiene datos no válidos.");

        assertThatThrownBy(() -> registration(new BigDecimal("1000"), new BigDecimal("2"), new BigDecimal("8")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("El nombre del equipo se recorta y se normalizan los espacios")
    void normalizaElEquipo() {
        TemperatureLogRegistration registration =
                registration(new BigDecimal("4"), new BigDecimal("2"), new BigDecimal("8"));

        assertThat(registration.equipment()).isEqualTo("Cámara 1");
        assertThat(registration.valueC()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("Sin equipo o con un nombre demasiado corto la lectura no es válida")
    void exigeEquipo() {
        assertThatThrownBy(() -> new TemperatureLogRegistration(
                        TemperatureKind.PREPARATION,
                        " ",
                        new BigDecimal("4"),
                        new BigDecimal("2"),
                        new BigDecimal("8"),
                        null))
                .isInstanceOf(ApiException.class);

        assertThatThrownBy(() -> new TemperatureLogRegistration(
                        TemperatureKind.PREPARATION,
                        "A",
                        new BigDecimal("4"),
                        new BigDecimal("2"),
                        new BigDecimal("8"),
                        null))
                .isInstanceOf(ApiException.class);
    }

    private static TemperatureLogRegistration registration(BigDecimal value, BigDecimal min, BigDecimal max) {
        return new TemperatureLogRegistration(TemperatureKind.EQUIPMENT, "  Cámara   1 ", value, min, max, null);
    }
}
