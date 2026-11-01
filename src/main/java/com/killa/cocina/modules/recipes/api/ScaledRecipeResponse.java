package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.ScaledRecipe;
import java.math.BigDecimal;
import java.util.List;

/**
 * Receta escalada a N raciones (REC-03, REC-04). No persiste nada.
 *
 * @param costComplete {@code false} si algún ingrediente no tiene coste; en ese caso
 *     {@code costPerRation} y {@code totalCost} valen {@code null}
 * @param missingProducts productos que impiden cerrar el coste
 */
public record ScaledRecipeResponse(
        long recipeId,
        long versionId,
        int versionNumber,
        int rations,
        BigDecimal costPerRation,
        BigDecimal totalCost,
        boolean costComplete,
        List<Long> missingProducts,
        List<ScaledIngredientResponse> ingredients) {

    public static ScaledRecipeResponse from(ScaledRecipe recipe) {
        return new ScaledRecipeResponse(
                recipe.recipeId(),
                recipe.versionId(),
                recipe.versionNumber(),
                recipe.rations(),
                recipe.costPerRation(),
                recipe.totalCost(),
                recipe.costComplete(),
                recipe.missingProducts(),
                recipe.ingredients().stream()
                        .map(ScaledIngredientResponse::from)
                        .toList());
    }
}
