package com.killa.cocina.modules.production.domain;

import java.time.Instant;

/**
 * Ejecución real de un ítem del plan (PRD-03, PRD-04).
 *
 * <p>El stock se descuenta al pasar a {@code READY}; por eso cancelar solo se permite antes y una
 * terminada no vuelve atrás (el kardex es inmutable). {@code rations} es lo realmente producido y
 * {@code plannedRations} la foto de lo previsto en el momento de empezar.</p>
 */
public record ProductionBatch(
        Long id,
        long establishmentId,
        long planItemId,
        long recipeVersionId,
        int plannedRations,
        int rations,
        ProductionStatus status,
        Instant startedAt,
        long startedBy,
        Instant finishedAt,
        Long finishedBy,
        String deviationReason) {

    /** Desviación previsto vs real (PRD-05). */
    public int deviation() {
        return rations - plannedRations;
    }

    /** Inicia la producción con las raciones previstas; no toca stock. */
    public static ProductionBatch start(
            long establishmentId, long planItemId, long recipeVersionId, int plannedRations, long actor, Instant now) {
        return new ProductionBatch(
                null,
                establishmentId,
                planItemId,
                recipeVersionId,
                plannedRations,
                plannedRations,
                ProductionStatus.IN_PROGRESS,
                now,
                actor,
                null,
                null,
                null);
    }
}
