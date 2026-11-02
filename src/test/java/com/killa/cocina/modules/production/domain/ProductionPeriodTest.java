package com.killa.cocina.modules.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Periodo de los informes de producción (PRD-05, ECO-03).
 *
 * <p>El día de negocio es {@code Europe/Madrid}: el informe debe empezar y terminar en la medianoche
 * local, no en la UTC, también cuando cambia el horario de verano.</p>
 */
class ProductionPeriodTest {

    @Test
    @DisplayName("El día de negocio se convierte desde Europe/Madrid en invierno y en verano")
    void convierteElDiaDeNegocio() {
        ProductionPeriod winter = new ProductionPeriod(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15));
        assertThat(winter.startInclusive()).isEqualTo(Instant.parse("2026-01-14T23:00:00Z"));
        assertThat(winter.endExclusive()).isEqualTo(Instant.parse("2026-01-15T23:00:00Z"));

        ProductionPeriod summer = new ProductionPeriod(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15));
        assertThat(summer.startInclusive()).isEqualTo(Instant.parse("2026-07-14T22:00:00Z"));
        assertThat(summer.endExclusive()).isEqualTo(Instant.parse("2026-07-15T22:00:00Z"));
    }

    @Test
    @DisplayName("El rango admite 31 días naturales y rechaza uno más")
    void acotaElRango() {
        ProductionPeriod max = new ProductionPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(max.endExclusive()).isAfter(max.startInclusive());

        assertThatThrownBy(() -> new ProductionPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(failure.details())
                            .anySatisfy(detail -> assertThat(detail.message()).contains("31 días"));
                });
    }

    @Test
    @DisplayName("El rango no puede ir hacia atrás")
    void rechazaElRangoInvertido() {
        assertThatThrownBy(() -> new ProductionPeriod(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("Las fechas del informe son obligatorias")
    void exigeLasFechas() {
        assertThatThrownBy(() -> new ProductionPeriod(null, LocalDate.of(2026, 1, 1)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new ProductionPeriod(LocalDate.of(2026, 1, 1), null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
