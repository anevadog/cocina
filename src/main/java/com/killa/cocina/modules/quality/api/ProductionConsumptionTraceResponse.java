package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.ProductionConsumptionTrace;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Consumo de un lote por una preparación (CAL-01). */
public record ProductionConsumptionTraceResponse(
        long batchId,
        long planId,
        LocalDate serviceDate,
        String serviceType,
        Long recipeId,
        String recipeName,
        int recipeVersionNumber,
        BigDecimal quantityBase,
        Instant occurredAt) {

    public static ProductionConsumptionTraceResponse from(ProductionConsumptionTrace trace) {
        return new ProductionConsumptionTraceResponse(
                trace.batchId(),
                trace.planId(),
                trace.serviceDate(),
                trace.serviceType(),
                trace.recipeId(),
                trace.recipeName(),
                trace.recipeVersionNumber(),
                trace.quantityBase(),
                trace.occurredAt());
    }
}
