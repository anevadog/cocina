package com.killa.cocina.modules.production.domain;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
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
        int validRations = ProductionLimits.rations(plannedRations, "plannedRations");
        return new ProductionBatch(
                null,
                establishmentId,
                planItemId,
                recipeVersionId,
                validRations,
                validRations,
                ProductionStatus.IN_PROGRESS,
                now,
                actor,
                null,
                null,
                null);
    }

    /**
     * Termina la producción con las raciones reales (PRD-03, PRD-05).
     *
     * <p>Una desviación distinta de cero exige motivo; el motivo se normaliza y se guarda tal cual
     * para que el informe pueda explicar la diferencia.</p>
     */
    public ProductionBatch finish(int actualRations, String reason, long actor, Instant now) {
        requireTransition(ProductionStatus.READY);
        int actual = ProductionLimits.rations(actualRations, "rations");
        String normalizedReason = ProductionLimits.reason(reason);
        if (actual != plannedRations && normalizedReason == null) {
            throw ProductionLimits.invalid(
                    "deviationReason", "es obligatorio cuando las raciones producidas difieren de las previstas");
        }
        return new ProductionBatch(
                id,
                establishmentId,
                planItemId,
                recipeVersionId,
                plannedRations,
                actual,
                ProductionStatus.READY,
                startedAt,
                startedBy,
                now,
                actor,
                normalizedReason);
    }

    /** Cancela una producción en curso; no descuenta stock ni fija fecha de fin. */
    public ProductionBatch cancel() {
        requireTransition(ProductionStatus.CANCELLED);
        return new ProductionBatch(
                id,
                establishmentId,
                planItemId,
                recipeVersionId,
                plannedRations,
                rations,
                ProductionStatus.CANCELLED,
                startedAt,
                startedBy,
                null,
                null,
                null);
    }

    /** Marca como servida una producción ya terminada; el consumo ya está en el kardex. */
    public ProductionBatch serve() {
        requireTransition(ProductionStatus.SERVED);
        return new ProductionBatch(
                id,
                establishmentId,
                planItemId,
                recipeVersionId,
                plannedRations,
                rations,
                ProductionStatus.SERVED,
                startedAt,
                startedBy,
                finishedAt,
                finishedBy,
                deviationReason);
    }

    private void requireTransition(ProductionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE,
                    "La producción está en " + status + " y no puede pasar a " + target + ".");
        }
    }
}
