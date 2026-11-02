package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.quality.domain.Incident;
import com.killa.cocina.modules.quality.domain.IncidentSeverity;
import com.killa.cocina.modules.quality.domain.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code incidents}. */
@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "incidents_seq")
    @SequenceGenerator(name = "incidents_seq", sequenceName = "incidents_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(length = 500)
    private String resolution;

    @Column(name = "opened_by", nullable = false)
    private long openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected IncidentEntity() {}

    private IncidentEntity(Incident incident) {
        apply(incident);
    }

    static IncidentEntity from(Incident incident) {
        return new IncidentEntity(incident);
    }

    void apply(Incident incident) {
        this.id = incident.id();
        this.establishmentId = incident.establishmentId();
        this.description = incident.description();
        this.severity = incident.severity();
        this.status = incident.status();
        this.resolution = incident.resolution();
        this.openedBy = incident.openedBy();
        this.openedAt = incident.openedAt();
        this.updatedAt = incident.updatedAt();
        this.closedBy = incident.closedBy();
        this.closedAt = incident.closedAt();
    }

    Incident toDomain() {
        return new Incident(
                id,
                establishmentId,
                description,
                severity,
                status,
                resolution,
                openedBy,
                openedAt,
                updatedAt,
                closedBy,
                closedAt);
    }
}
