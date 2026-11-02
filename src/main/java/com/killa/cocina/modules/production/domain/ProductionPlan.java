package com.killa.cocina.modules.production.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Cabecera del plan de producción de una fecha y un servicio (PRD-01).
 *
 * <p>El estado se guarda para poder filtrar, pero se recalcula desde los batches: la verdad está en
 * las ejecuciones, no en un contador que pueda desincronizarse (ADR-036).</p>
 */
public record ProductionPlan(
        Long id,
        long establishmentId,
        LocalDate serviceDate,
        ServiceType serviceType,
        ProductionStatus status,
        long createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductionPlan create(
            long establishmentId, ProductionPlanRegistration registration, long actor, Instant now) {
        return new ProductionPlan(
                null,
                establishmentId,
                registration.serviceDate(),
                registration.serviceType(),
                ProductionStatus.PENDING,
                actor,
                now,
                now);
    }

    public ProductionPlan withStatus(ProductionStatus newStatus, Instant now) {
        return new ProductionPlan(id, establishmentId, serviceDate, serviceType, newStatus, createdBy, createdAt, now);
    }

    /** Marca el plan como modificado sin cambiar su estado. */
    public ProductionPlan touched(Instant now) {
        return new ProductionPlan(id, establishmentId, serviceDate, serviceType, status, createdBy, createdAt, now);
    }

    public boolean isPending() {
        return status == ProductionStatus.PENDING;
    }
}
