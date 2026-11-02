package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.IncidentSeverity;
import com.killa.cocina.modules.quality.domain.IncidentStatus;
import com.killa.cocina.modules.quality.domain.IncidentView;
import java.time.Instant;

/** Incidencia expuesta por la API (CAL-05). */
public record IncidentResponse(
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

    public static IncidentResponse from(IncidentView view) {
        return new IncidentResponse(
                view.id(),
                view.description(),
                view.severity(),
                view.status(),
                view.resolution(),
                view.openedBy(),
                view.openedAt(),
                view.updatedAt(),
                view.closedBy(),
                view.closedAt());
    }
}
