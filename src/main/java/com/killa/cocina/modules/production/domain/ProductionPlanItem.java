package com.killa.cocina.modules.production.domain;

/**
 * Línea del plan: una versión publicada de receta y las raciones previstas (PRD-01).
 *
 * <p>La versión se fija al planificar: si la receta se versiona después, este plan sigue usando
 * exactamente la versión que se registró (ADR-036).</p>
 */
public record ProductionPlanItem(Long id, long establishmentId, long planId, long recipeVersionId, int plannedRations) {

    public static ProductionPlanItem create(
            long establishmentId, long planId, long recipeVersionId, int plannedRations) {
        return new ProductionPlanItem(
                null,
                establishmentId,
                planId,
                recipeVersionId,
                ProductionLimits.rations(plannedRations, "plannedRations"));
    }
}
