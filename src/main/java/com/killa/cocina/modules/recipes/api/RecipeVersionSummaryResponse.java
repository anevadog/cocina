package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeVersionStatus;
import com.killa.cocina.modules.recipes.domain.RecipeVersionSummary;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resumen de una versión para el historial (REC-05, ECO-01).
 *
 * @param totalCost coste congelado del lote base; {@code null} en un borrador
 * @param costPerRation coste congelado por ración; {@code null} en un borrador
 */
public record RecipeVersionSummaryResponse(
        Long id,
        int versionNumber,
        RecipeVersionStatus status,
        int baseRations,
        BigDecimal totalCost,
        BigDecimal costPerRation,
        Instant publishedAt) {

    public static RecipeVersionSummaryResponse from(RecipeVersionSummary summary) {
        return new RecipeVersionSummaryResponse(
                summary.id(),
                summary.versionNumber(),
                summary.status(),
                summary.baseRations(),
                summary.totalCost(),
                summary.costPerRation(),
                summary.publishedAt());
    }
}
