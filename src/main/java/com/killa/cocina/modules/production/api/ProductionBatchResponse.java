package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionBatchView;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import java.time.Instant;

/** Ejecución de un ítem del plan (PRD-03, PRD-05). */
public record ProductionBatchResponse(
        Long id,
        ProductionStatus status,
        int plannedRations,
        int rations,
        int deviation,
        String deviationReason,
        Instant startedAt,
        Long startedBy,
        Instant finishedAt,
        Long finishedBy) {

    public static ProductionBatchResponse from(ProductionBatchView view) {
        return new ProductionBatchResponse(
                view.id(),
                view.status(),
                view.plannedRations(),
                view.rations(),
                view.deviation(),
                view.deviationReason(),
                view.startedAt(),
                view.startedBy(),
                view.finishedAt(),
                view.finishedBy());
    }
}
