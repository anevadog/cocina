package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeVersionStatus;
import com.killa.cocina.modules.recipes.domain.RecipeVersionView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Versión completa de una receta (REC-04, REC-05).
 *
 * @param frozenTotalCost coste congelado del lote base al publicar; {@code null} en un borrador
 * @param frozenCostPerRation coste congelado por ración; {@code null} en un borrador
 * @param currentTotalCost coste actual del lote base con el PMP vigente
 * @param currentCostPerRation coste actual por ración con el PMP vigente
 */
public record RecipeVersionResponse(
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
        List<RecipeItemResponse> items) {

    public static RecipeVersionResponse from(RecipeVersionView view) {
        return new RecipeVersionResponse(
                view.id(),
                view.recipeId(),
                view.versionNumber(),
                view.status(),
                view.baseRations(),
                view.yieldPct(),
                view.prepTimeMinutes(),
                view.instructions(),
                view.publishedAt(),
                view.publishedBy(),
                view.createdBy(),
                view.createdAt(),
                view.updatedAt(),
                view.frozenTotalCost(),
                view.frozenCostPerRation(),
                view.currentTotalCost(),
                view.currentCostPerRation(),
                view.items().stream().map(RecipeItemResponse::from).toList());
    }
}
