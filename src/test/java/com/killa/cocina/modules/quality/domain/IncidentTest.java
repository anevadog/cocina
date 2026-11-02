package com.killa.cocina.modules.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de una incidencia (CAL-05, ADR-040). */
class IncidentTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-02T10:00:00Z");

    private static final Instant UPDATED_AT = Instant.parse("2026-11-02T11:00:00Z");

    @Test
    @DisplayName("La descripción se recorta y la severidad es obligatoria")
    void normalizaLaDescripcion() {
        IncidentRegistration registration =
                new IncidentRegistration("  Cámara   con   hielo acumulado ", IncidentSeverity.HIGH);

        assertThat(registration.description()).isEqualTo("Cámara con hielo acumulado");
        assertThatThrownBy(() -> new IncidentRegistration("corta", IncidentSeverity.LOW))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new IncidentRegistration("x".repeat(1001), IncidentSeverity.LOW))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new IncidentRegistration("Descripción válida", null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("La incidencia nace OPEN, sin resolución ni datos de cierre")
    void abreLaIncidencia() {
        Incident incident = Incident.open(
                1L, new IncidentRegistration("Cámara con hielo acumulado", IncidentSeverity.CRITICAL), 7L, OPENED_AT);

        assertThat(incident.id()).isNull();
        assertThat(incident.status()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.resolution()).isNull();
        assertThat(incident.openedBy()).isEqualTo(7L);
        assertThat(incident.openedAt()).isEqualTo(OPENED_AT);
        assertThat(incident.updatedAt()).isEqualTo(OPENED_AT);
        assertThat(incident.closedBy()).isNull();
        assertThat(incident.closedAt()).isNull();
    }

    @Test
    @DisplayName("OPEN → IN_PROGRESS conserva la apertura y no admite resolución")
    void iniciaLaResolucion() {
        Incident started = incident().transitionTo(IncidentStatus.IN_PROGRESS, null, 8L, UPDATED_AT);

        assertThat(started.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(started.resolution()).isNull();
        assertThat(started.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(started.closedBy()).isNull();
        assertThatThrownBy(
                        () -> incident().transitionTo(IncidentStatus.IN_PROGRESS, "Ya está resuelta", 8L, UPDATED_AT))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("Cerrar exige resolución y registra actor y fecha")
    void cierraConResolucion() {
        Incident closed = incident().transitionTo(IncidentStatus.CLOSED, "  Se   limpió   y   revisó ", 9L, UPDATED_AT);

        assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.resolution()).isEqualTo("Se limpió y revisó");
        assertThat(closed.closedBy()).isEqualTo(9L);
        assertThat(closed.closedAt()).isEqualTo(UPDATED_AT);
        assertThat(closed.updatedAt()).isEqualTo(UPDATED_AT);

        assertThatThrownBy(() -> incident().transitionTo(IncidentStatus.CLOSED, null, 9L, UPDATED_AT))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(error.details())
                            .extracting(detail -> detail.field())
                            .containsExactly("resolution");
                });
        assertThatThrownBy(() -> incident().transitionTo(IncidentStatus.CLOSED, "mal", 9L, UPDATED_AT))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Volver a OPEN es un error de validación")
    void noSeReabre() {
        assertThatThrownBy(() -> incident().transitionTo(IncidentStatus.OPEN, null, 8L, UPDATED_AT))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(error.details())
                            .extracting(detail -> detail.field())
                            .containsExactly("status");
                });
    }

    @Test
    @DisplayName("Desde CLOSED cualquier cambio es un conflicto")
    void unaCerradaNoCambia() {
        Incident closed = incident().transitionTo(IncidentStatus.CLOSED, "Resuelta del todo", 9L, UPDATED_AT);

        assertThatThrownBy(() -> closed.transitionTo(IncidentStatus.IN_PROGRESS, null, 9L, UPDATED_AT))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE));
    }

    @Test
    @DisplayName("Una transición no permitida se rechaza")
    void rechazaTransicionesNoPermitidas() {
        Incident started = incident().transitionTo(IncidentStatus.IN_PROGRESS, null, 8L, UPDATED_AT);

        assertThatThrownBy(() -> started.transitionTo(IncidentStatus.IN_PROGRESS, null, 8L, UPDATED_AT))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static Incident incident() {
        return Incident.open(
                1L, new IncidentRegistration("Cámara con hielo acumulado", IncidentSeverity.HIGH), 7L, OPENED_AT);
    }
}
