package com.killa.cocina.modules.quality.domain;

import java.time.Instant;

/** Incidencia expuesta por la API (CAL-05). */
public record IncidentView(
        Long id,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        String resolution,
        long openedBy,
        Instant openedAt,
        Instant updatedAt,
        Long closedBy,
        Instant closedAt) {

    public static IncidentView from(Incident incident) {
        return new IncidentView(
                incident.id(),
                incident.description(),
                incident.severity(),
                incident.status(),
                incident.resolution(),
                incident.openedBy(),
                incident.openedAt(),
                incident.updatedAt(),
                incident.closedBy(),
                incident.closedAt());
    }
}
