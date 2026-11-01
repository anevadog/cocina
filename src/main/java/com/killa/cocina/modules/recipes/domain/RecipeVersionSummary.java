package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resumen de una versión para listados e historial (REC-05, ECO-01).
 *
 * <p>En una versión publicada, {@code totalCost} y {@code costPerRation} son el coste congelado del
 * momento de publicar: el histórico no se recalcula con los precios de hoy.</p>
 */
public record RecipeVersionSummary(
        Long id,
        int versionNumber,
        RecipeVersionStatus status,
        int baseRations,
        BigDecimal totalCost,
        BigDecimal costPerRation,
        Instant publishedAt) {

    public static RecipeVersionSummary from(RecipeVersion version) {
        return new RecipeVersionSummary(
                version.id(),
                version.versionNumber(),
                version.status(),
                version.baseRations(),
                version.totalCost(),
                version.costPerRation(),
                version.publishedAt());
    }
}
