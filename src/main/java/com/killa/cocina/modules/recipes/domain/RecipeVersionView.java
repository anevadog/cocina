package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Versión completa de una receta (REC-01, REC-04, REC-05).
 *
 * <p>Los costes congelados solo existen en versiones publicadas. Los «actuales» se recalculan con el
 * PMP vigente y se rellenan al consultar (ECO-01); en un borrador no hay coste congelado.</p>
 */
public record RecipeVersionView(
        Long id,
        long recipeId,
        int versionNumber,
        RecipeVersionStatus status,
        int baseRations,
        BigDecimal yieldPct,
        Integer prepTimeMinutes,
        String instructions,
        Instant publishedAt,
        Long publishedBy,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal frozenTotalCost,
        BigDecimal frozenCostPerRation,
        BigDecimal currentTotalCost,
        BigDecimal currentCostPerRation,
        List<RecipeItemView> items) {

    public RecipeVersionView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
