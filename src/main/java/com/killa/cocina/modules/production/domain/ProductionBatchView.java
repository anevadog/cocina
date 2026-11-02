package com.killa.cocina.modules.production.domain;

import java.time.Instant;

/** Batch expuesto por la API, con la desviación ya calculada (PRD-05). */
public record ProductionBatchView(
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

    public static ProductionBatchView from(ProductionBatch batch) {
        return new ProductionBatchView(
                batch.id(),
                batch.status(),
                batch.plannedRations(),
                batch.rations(),
                batch.deviation(),
                batch.deviationReason(),
                batch.startedAt(),
                batch.startedBy(),
                batch.finishedAt(),
                batch.finishedBy());
    }
}
