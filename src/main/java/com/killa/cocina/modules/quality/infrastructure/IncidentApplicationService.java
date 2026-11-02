package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.quality.domain.Incident;
import com.killa.cocina.modules.quality.domain.IncidentRegistration;
import com.killa.cocina.modules.quality.domain.IncidentRepository;
import com.killa.cocina.modules.quality.domain.IncidentService;
import com.killa.cocina.modules.quality.domain.IncidentStatus;
import com.killa.cocina.modules.quality.domain.IncidentView;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de incidencias (CAL-05, ADR-040).
 *
 * <p>La apertura y los cambios de estado se auditan en la misma transacción. El bloqueo de la fila
 * evita que dos cierres simultáneos se pisen.</p>
 */
@Service
@Transactional
public class IncidentApplicationService implements IncidentService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String ENTITY_TYPE = "incident";

    private final IncidentRepository incidents;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public IncidentApplicationService(
            IncidentRepository incidents, AuditRecorder audit, ObjectMapper objectMapper, Clock clock) {
        this.incidents = incidents;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public IncidentView register(long establishmentId, Long actorId, IncidentRegistration registration) {
        long actor = requireActor(actorId);
        Instant now = Instant.now(clock);

        Incident saved = incidents.save(Incident.open(establishmentId, registration, actor, now));
        audit.record(
                establishmentId, actor, AuditAction.INCIDENT_OPENED, ENTITY_TYPE, saved.id(), null, snapshot(saved));
        return IncidentView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentView find(long establishmentId, long incidentId) {
        return IncidentView.from(requireIncident(establishmentId, incidentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<IncidentView> list(long establishmentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<IncidentView> items = incidents.findPage(establishmentId, safePage, safeSize).stream()
                .map(IncidentView::from)
                .toList();
        return new PageResult<>(items, incidents.count(establishmentId), safePage, safeSize);
    }

    @Override
    public IncidentView changeStatus(
            long establishmentId, Long actorId, long incidentId, IncidentStatus target, String resolution) {
        long actor = requireActor(actorId);
        Instant now = Instant.now(clock);
        Incident current = incidents
                .findByIdForUpdate(establishmentId, incidentId)
                .orElseThrow(() ->
                        new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "La incidencia " + incidentId + " no existe."));

        Incident updated = incidents.save(current.transitionTo(target, resolution, actor, now));
        audit.record(
                establishmentId,
                actor,
                AuditAction.INCIDENT_STATUS_CHANGED,
                ENTITY_TYPE,
                updated.id(),
                snapshot(current),
                snapshot(updated));
        return IncidentView.from(updated);
    }

    private Incident requireIncident(long establishmentId, long incidentId) {
        return incidents
                .findById(establishmentId, incidentId)
                .orElseThrow(() ->
                        new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "La incidencia " + incidentId + " no existe."));
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "El registro de incidencias exige un usuario autenticado.");
        }
        return actorId;
    }

    private String snapshot(Incident incident) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("incidentId", incident.id());
        values.put("severity", incident.severity().name());
        values.put("status", incident.status().name());
        values.put("resolution", incident.resolution());
        values.put("openedBy", incident.openedBy());
        values.put("openedAt", incident.openedAt().toString());
        values.put("updatedAt", incident.updatedAt().toString());
        values.put("closedBy", incident.closedBy());
        values.put(
                "closedAt",
                incident.closedAt() == null ? null : incident.closedAt().toString());
        return objectMapper.writeValueAsString(values);
    }
}
