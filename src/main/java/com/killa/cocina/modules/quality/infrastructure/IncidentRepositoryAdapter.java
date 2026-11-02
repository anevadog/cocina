package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.quality.domain.Incident;
import com.killa.cocina.modules.quality.domain.IncidentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link IncidentRepository}. */
@Repository
class IncidentRepositoryAdapter implements IncidentRepository {

    private final IncidentJpaRepository incidents;

    IncidentRepositoryAdapter(IncidentJpaRepository incidents) {
        this.incidents = incidents;
    }

    @Override
    public Incident save(Incident incident) {
        IncidentEntity entity = incident.id() == null
                ? IncidentEntity.from(incident)
                : incidents.findById(incident.id()).orElseGet(() -> IncidentEntity.from(incident));
        if (incident.id() != null) {
            entity.apply(incident);
        }
        return incidents.save(entity).toDomain();
    }

    @Override
    public Optional<Incident> findById(long establishmentId, long incidentId) {
        return incidents.findByIdAndEstablishmentId(incidentId, establishmentId).map(IncidentEntity::toDomain);
    }

    @Override
    public Optional<Incident> findByIdForUpdate(long establishmentId, long incidentId) {
        return incidents
                .findByIdAndEstablishmentIdForUpdate(incidentId, establishmentId)
                .map(IncidentEntity::toDomain);
    }

    @Override
    public List<Incident> findPage(long establishmentId, int page, int size) {
        return incidents
                .findByEstablishmentIdOrderByOpenedAtDescIdDesc(establishmentId, PageRequest.of(page, size))
                .stream()
                .map(IncidentEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId) {
        return incidents.countByEstablishmentId(establishmentId);
    }
}
