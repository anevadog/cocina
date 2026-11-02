package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.shared.pagination.PageResult;

/**
 * Casos de uso de incidencias (CAL-05).
 *
 * <p>La implementación transaccional vive en {@code infrastructure} (ADR-020).</p>
 */
public interface IncidentService {

    IncidentView register(long establishmentId, Long actorId, IncidentRegistration registration);

    IncidentView find(long establishmentId, long incidentId);

    PageResult<IncidentView> list(long establishmentId, int page, int size);

    /** Cambia el estado; al cerrar exige resolución. */
    IncidentView changeStatus(
            long establishmentId, Long actorId, long incidentId, IncidentStatus target, String resolution);
}
