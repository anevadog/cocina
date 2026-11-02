package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.quality.domain.TemperatureKind;
import com.killa.cocina.modules.quality.domain.TemperatureLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo JPA de {@code temperature_logs}. */
@Entity
@Table(name = "temperature_logs")
public class TemperatureLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "temperature_logs_seq")
    @SequenceGenerator(name = "temperature_logs_seq", sequenceName = "temperature_logs_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemperatureKind kind;

    @Column(nullable = false, length = 80)
    private String equipment;

    @Column(name = "value_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal valueC;

    @Column(name = "min_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal minC;

    @Column(name = "max_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxC;

    /** Columna generada por PostgreSQL: nunca se escribe desde JPA. */
    @Column(name = "within_range", nullable = false, insertable = false, updatable = false)
    private boolean withinRange;

    @Column(name = "actor_id", nullable = false)
    private long actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TemperatureLogEntity() {}

    private TemperatureLogEntity(TemperatureLog log) {
        apply(log);
    }

    static TemperatureLogEntity from(TemperatureLog log) {
        return new TemperatureLogEntity(log);
    }

    void apply(TemperatureLog log) {
        this.id = log.id();
        this.establishmentId = log.establishmentId();
        this.kind = log.kind();
        this.equipment = log.equipment();
        this.valueC = log.valueC();
        this.minC = log.minC();
        this.maxC = log.maxC();
        this.withinRange = log.withinRange();
        this.actorId = log.actorId();
        this.recordedAt = log.recordedAt();
        this.createdAt = log.createdAt();
    }

    TemperatureLog toDomain() {
        return new TemperatureLog(
                id, establishmentId, kind, equipment, valueC, minC, maxC, withinRange, actorId, recordedAt, createdAt);
    }
}
