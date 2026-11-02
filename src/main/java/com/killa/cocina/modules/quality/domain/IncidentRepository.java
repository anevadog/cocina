package com.killa.cocina.modules.quality.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de incidencias. */
public interface IncidentRepository {

    Incident save(Incident incident);

    Optional<Incident> findById(long establishmentId, long incidentId);

    /** Bloquea la incidencia para que dos transiciones simultáneas no se pisen. */
    Optional<Incident> findByIdForUpdate(long establishmentId, long incidentId);

    /** Más recientes primero por fecha de apertura. */
    List<Incident> findPage(long establishmentId, int page, int size);

    long count(long establishmentId);
}
