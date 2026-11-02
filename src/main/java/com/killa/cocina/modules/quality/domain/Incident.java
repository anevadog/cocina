package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;

/**
 * Incidencia de calidad (CAL-05, ADR-040).
 *
 * <p>La descripción y la severidad se fijan al abrirla. Solo cambian el estado y, al cerrar, la
 * resolución. Una incidencia cerrada no se reabre: eso mantiene el histórico fiel a lo ocurrido.</p>
 */
public record Incident(
        Long id,
        long establishmentId,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        String resolution,
        long openedBy,
        Instant openedAt,
        Instant updatedAt,
        Long closedBy,
        Instant closedAt) {

    /** Longitud mínima de la resolución. */
    public static final int MIN_RESOLUTION_LENGTH = 5;

    /** Longitud máxima de la resolución (coincide con varchar(500)). */
    public static final int MAX_RESOLUTION_LENGTH = 500;

    public Incident {
        if (establishmentId <= 0) {
            throw IncidentRegistration.invalid("establishmentId", "es obligatorio");
        }
        description = IncidentRegistration.normalizeDescription(description);
        if (severity == null) {
            throw IncidentRegistration.invalid("severity", "es obligatoria");
        }
        if (status == null) {
            throw IncidentRegistration.invalid("status", "es obligatorio");
        }
        if (openedBy <= 0) {
            throw IncidentRegistration.invalid("openedBy", "es obligatorio");
        }
        if (openedAt == null) {
            throw IncidentRegistration.invalid("openedAt", "es obligatorio");
        }
        if (updatedAt == null) {
            throw IncidentRegistration.invalid("updatedAt", "es obligatorio");
        }
        if (status == IncidentStatus.CLOSED) {
            resolution = normalizeResolution(resolution);
            if (closedBy == null || closedBy <= 0) {
                throw IncidentRegistration.invalid("closedBy", "es obligatorio al cerrar");
            }
            if (closedAt == null) {
                throw IncidentRegistration.invalid("closedAt", "es obligatorio al cerrar");
            }
        } else if (resolution != null || closedBy != null || closedAt != null) {
            throw IncidentRegistration.invalid("resolution", "solo se admite al cerrar la incidencia");
        }
    }

    /** Abre una incidencia en estado {@code OPEN}. */
    public static Incident open(long establishmentId, IncidentRegistration registration, long actorId, Instant now) {
        return new Incident(
                null,
                establishmentId,
                registration.description(),
                registration.severity(),
                IncidentStatus.OPEN,
                null,
                actorId,
                now,
                now,
                null,
                null);
    }

    /**
     * Cambia el estado aplicando las transiciones válidas.
     *
     * <p>{@code OPEN} solo se admite al crear; volver a él es un error de validación. Cualquier
     * cambio desde {@code CLOSED} es un conflicto porque el cierre es definitivo. La resolución solo
     * viaja al cerrar y es obligatoria en ese momento.</p>
     */
    public Incident transitionTo(IncidentStatus target, String resolution, long actorId, Instant now) {
        if (target == null) {
            throw IncidentRegistration.invalid("status", "es obligatorio");
        }
        if (target == IncidentStatus.OPEN) {
            throw IncidentRegistration.invalid("status", "no se puede volver a OPEN");
        }
        if (status == IncidentStatus.CLOSED) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE, "La incidencia " + id + " ya está cerrada y no admite cambios.");
        }
        if (target == status) {
            throw IncidentRegistration.invalid("status", "la incidencia ya está en ese estado");
        }
        if (!status.canTransitionTo(target)) {
            throw IncidentRegistration.invalid("status", "transición no permitida desde " + status);
        }
        if (target == IncidentStatus.CLOSED) {
            return new Incident(
                    id,
                    establishmentId,
                    description,
                    severity,
                    IncidentStatus.CLOSED,
                    normalizeResolution(resolution),
                    openedBy,
                    openedAt,
                    now,
                    actorId,
                    now);
        }
        if (resolution != null && !resolution.isBlank()) {
            throw IncidentRegistration.invalid("resolution", "solo se admite al cerrar la incidencia");
        }
        return new Incident(
                id, establishmentId, description, severity, target, null, openedBy, openedAt, now, null, null);
    }

    private static String normalizeResolution(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw IncidentRegistration.invalid("resolution", "es obligatoria al cerrar la incidencia");
        }
        String normalized = resolution.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_RESOLUTION_LENGTH || normalized.length() > MAX_RESOLUTION_LENGTH) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation(
                            "resolution",
                            "debe tener entre " + MIN_RESOLUTION_LENGTH + " y " + MAX_RESOLUTION_LENGTH
                                    + " caracteres")));
        }
        return normalized;
    }
}
